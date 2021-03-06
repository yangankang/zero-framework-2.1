package com.yoosal.orm.mapping;

import com.yoosal.common.AnnotationUtils;
import com.yoosal.common.CollectionUtils;
import com.yoosal.common.Logger;
import com.yoosal.common.StringUtils;
import com.yoosal.orm.annotation.Column;
import com.yoosal.orm.annotation.DefaultValue;
import com.yoosal.orm.annotation.Table;
import com.yoosal.orm.core.DataSourceManager;
import com.yoosal.orm.core.GroupDataSource;
import com.yoosal.orm.dialect.SQLDialect;
import com.yoosal.orm.dialect.SQLDialectFactory;
import com.yoosal.orm.exception.OrmMappingException;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultDBMapping implements DBMapping {
    private static final Logger logger = Logger.getLogger(DefaultDBMapping.class);
    private DataSourceManager dataSourceManager;
    private Set<Class> classes;
    private Map<Class, TableModel> mappingModelMap = new HashMap<Class, TableModel>();

    @Override
    public void doMapping(DataSourceManager dataSourceManager, Set<Class> classes, boolean canAlter) throws SQLException {
        this.dataSourceManager = dataSourceManager;
        this.classes = classes;

        classToModel(null);
        compareToTables(canAlter);
    }


    @Override
    public void doMapping(DataSourceManager dataSourceManager, Set<Class> classes, String convert, boolean canAlter) throws SQLException {
        this.dataSourceManager = dataSourceManager;
        this.classes = classes;

        classToModel(convert);
        compareToTables(canAlter);
    }

    @Override
    public void register(SQLDialect dialect) {
        SQLDialectFactory.registerSQLDialect(dialect);
    }

    @Override
    public SQLDialect getSQLDialect(DatabaseMetaData databaseMetaData) throws SQLException {
        return SQLDialectFactory.getSQLDialect(databaseMetaData);
    }

    @Override
    public TableModel getTableMapping(Class clazz) {
        if (clazz == null) {
            throw new OrmMappingException("can't find class [table]");
        }
        return mappingModelMap.get(clazz);
    }

    private void compareToTables(boolean canAlter) throws SQLException {
        Set<GroupDataSource> groupDataSources = this.dataSourceManager.getAllDataSource();
        for (GroupDataSource groupDataSource : groupDataSources) {
            compareToTable(groupDataSource, canAlter);
        }
    }

    private void compareToTable(GroupDataSource groupDataSource, boolean canAlter) throws SQLException {
        List<String> enumNames = groupDataSource.getEnumNames();
        List<TableModel> tableModels = new ArrayList<TableModel>();
        if (!CollectionUtils.isEmpty(enumNames)) {
            for (Map.Entry<Class, TableModel> entry : mappingModelMap.entrySet()) {
                if (enumNames.contains(entry.getValue().getJavaTableName())) {
                    tableModels.add(entry.getValue());
                }
            }
        } else {
            for (Map.Entry<Class, TableModel> entry : mappingModelMap.entrySet()) {
                tableModels.add(entry.getValue());
            }
        }

        Set<GroupDataSource.SourceObject> sourceObjects = groupDataSource.getSourceObjects();
        for (GroupDataSource.SourceObject sourceObject : sourceObjects) {
            DataSource dataSource = sourceObject.getDataSource();
            compareDatabase(tableModels, dataSource, canAlter);
        }
    }

    private void compareDatabase(List<TableModel> tableModels, DataSource dataSource, boolean canAlter) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            String[] type = {"Table"};
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet tableResultSet = databaseMetaData.getTables(connection.getCatalog(), null, null, type);
            List<String> tableNames = new ArrayList<String>();
            while (tableResultSet.next()) {
                String tableName = tableResultSet.getString("TABLE_NAME");
                tableNames.add(tableName.toLowerCase());
            }
            StringBuilder logs = new StringBuilder();
            long time = System.currentTimeMillis();
            for (TableModel tableModel : tableModels) {
                if (tableNames.contains(tableModel.getDbTableName().toLowerCase())) {

                    logs.append(tableModel.getDbTableName() + ",");

                    ResultSet columnResultSet = databaseMetaData.getColumns(connection.getCatalog(), null, tableModel.getDbTableName(), null);
                    List<ColumnModel> columnModels = tableModel.getMappingColumnModels();
                    List<ColumnModel> existColumns = new ArrayList<ColumnModel>();
                    for (ColumnModel cm : columnModels) {
                        existColumns.add(cm);
                    }
                    while (columnResultSet.next()) {
                        String columnName = columnResultSet.getString("COLUMN_NAME");
                        String typeName = columnResultSet.getString("TYPE_NAME");
                        int dataType = columnResultSet.getInt("DATA_TYPE");

                        String columnType = getSQLDialect(databaseMetaData).getType(dataType);
                        ColumnModel columnModel = null;
                        for (ColumnModel cm : columnModels) {
                            if (cm.getColumnName().equalsIgnoreCase(columnName)) {
                                columnModel = cm;
                                break;
                            }
                        }
                        if (columnModel != null) {
                            existColumns.remove(columnModel);
                            columnModel.setColumnType(columnType);
                            columnModel.setColumnTypeCode(dataType);
                        }
                    }
                    if (existColumns.size() > 0) {
                        if (canAlter) {
                            //如果可以修改数据库表结构，那么就将新增的字段增加到表中
                            this.alertAddColumn(dataSource, tableModel, existColumns);
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (ColumnModel cm : existColumns) {
                                sb.append(cm.getJavaName() + ":" + cm.getColumnName() + " ");
                            }
                            throw new OrmMappingException("can't alter table and columns mapping match the inconsistent " + sb.toString());
                        }
                    }
                } else {
                    if (canAlter) {
                        //如果可以修改数据库表结构，那么新增表
                        this.createTable(dataSource, tableModel);
                    } else {
                        throw new OrmMappingException("can't alter table mapping match the inconsistent " + tableModel.getJavaTableName() + ":" + tableModel.getDbTableName());
                    }
                }
            }
            connection.close();
            logger.info("匹配数据库和Java对象完成 : [" + logs + "] 耗时 : " + ((System.currentTimeMillis() - time) / 1000) + "s");
        } finally {
            ccs(connection, null);
        }
    }

    private void ccs(Connection connection, Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
            }
        }
    }

    private void alertAddColumn(DataSource dataSource, TableModel tableModel, List<ColumnModel> existColumns) throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            String sql = getSQLDialect(databaseMetaData).addColumn(tableModel, existColumns);
            statement = connection.createStatement();
            boolean success = statement.execute(sql);
        } finally {
            ccs(connection, statement);
        }
    }

    private void createTable(DataSource dataSource, TableModel tableModel) throws SQLException {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            String sql = getSQLDialect(databaseMetaData).createTable(tableModel);
            connection = dataSource.getConnection();
            statement = connection.createStatement();
            boolean success = statement.execute(sql);
        } finally {
            ccs(connection, statement);
        }
    }

    private synchronized void classToModel(String convert) {
        if (classes != null) {
            for (Class clazz : classes) {
                Table table = AnnotationUtils.findAnnotation(clazz, Table.class);
                if (table == null) continue;
                TableModel model = new TableModel();
                model.setWordConvert(convert);
                String tableName = table.value();
                if (StringUtils.isBlank(tableName)) {
                    tableName = clazz.getSimpleName();
                }
                model.setJavaTableName(tableName);
                String dataSourceName = table.dataSourceName();
                if (StringUtils.isNotBlank(dataSourceName)) {
                    model.setDataSourceName(dataSourceName);
                }
                model.convert();
                if (clazz.isEnum()) {
                    setEnumClass(clazz, model, convert, tableName);
                } else {
                    setBeanClass(clazz, model, convert);
                }
                mappingModelMap.put(clazz, model);
            }
        }
    }

    private void setEnumClass(Class clazz, TableModel model, String convert, String tableName) {
        Object[] objects = clazz.getEnumConstants();
        int i = 0;
        for (Object obj : objects) {
            ColumnModel columnModel = new ColumnModel();
            columnModel.setWordConvert(convert);
            columnModel.setCode(i);
            columnModel.setJavaName(obj.toString());
            Column column = null;
            try {
                column = clazz.getField(obj.toString()).getAnnotation(Column.class);
            } catch (NoSuchFieldException e) {
                throw new OrmMappingException("not find Column in field " + tableName + "." + obj.toString(), e);
            }
            setColumn(column, columnModel);
            model.addMappingColumnModel(columnModel);
            i++;
        }
    }

    private void setBeanClass(Class clazz, TableModel model, String convert) {
        Field[] fields = clazz.getDeclaredFields();
        int i = 0;
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            ColumnModel columnModel = new ColumnModel();
            columnModel.setWordConvert(convert);
            columnModel.setCode(i);
            columnModel.setJavaName(fieldName);
            Column column = field.getAnnotation(Column.class);
            if (column == null) {
                try {
                    Method method = clazz.getMethod(StringUtils.beanFieldToMethod(fieldName));
                    column = method.getAnnotation(Column.class);
                } catch (NoSuchMethodException e) {
                }
            }
            setColumn(column, columnModel);
            model.addMappingColumnModel(columnModel);
            i++;
        }
    }

    private void setColumn(Column column, ColumnModel columnModel) {
        String name = column == null ? null : column.name();
        if (StringUtils.isBlank(name)) name = null;
        Class type = column == null ? String.class : column.type();
        long length = column == null ? 255 : column.length();
        if (length == 255 && type == Integer.class) length = 11;
        if (length == 255 && type == Long.class) length = 13;
        if (length == 255 && type == Double.class) length = 16;
        if (length == 255 && type == Text.class) length = 0;
        boolean isPrimaryKey = (column == null ? false : column.key());
        Class generateStrategy = (column == null ? null : column.strategy());
        if (generateStrategy != null && generateStrategy.isAssignableFrom(Column.class))
            generateStrategy = null;
        boolean isLock = (column == null ? false : column.lock());

        boolean index = (column == null ? false : column.index());
        boolean allowNull = (column == null ? true : column.allowNull());
        DefaultValue defaultValue = (column == null ? null : column.defaultValue());
        String comment = (column == null ? null : column.comment());

        columnModel.setPrimaryKey(isPrimaryKey);
        columnModel.setJavaType(type);
        columnModel.setComment(comment);
        columnModel.setLength(length);
        columnModel.setGenerateStrategy(generateStrategy);
        columnModel.setLock(isLock);
        columnModel.setAllowNull(allowNull);
        columnModel.setDefaultValue(defaultValue);
        columnModel.setIndex(index);
        columnModel.convert();
    }
}
