package com.yoosal.orm.dialect;

import com.yoosal.common.CollectionUtils;
import com.yoosal.common.Logger;
import com.yoosal.common.StringUtils;
import com.yoosal.orm.ModelObject;
import com.yoosal.orm.annotation.DefaultValue;
import com.yoosal.orm.core.Batch;
import com.yoosal.orm.exception.SQLDialectException;
import com.yoosal.orm.mapping.ColumnModel;
import com.yoosal.orm.mapping.DBMapping;
import com.yoosal.orm.mapping.TableModel;
import com.yoosal.orm.query.Limit;
import com.yoosal.orm.query.OrderBy;
import com.yoosal.orm.query.Query;
import com.yoosal.orm.query.Wheres;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * 旧的方式，新的方式 {@link MysqlMiddleCreator}
 */
public abstract class StandardSQL implements SQLDialect {
    private static final Logger logger = Logger.getLogger(StandardSQL.class);
    protected static final Map<Integer, String> types = new HashMap<Integer, String>();
    protected static final Map<Class, String> typesMapping = new HashMap<Class, String>();
    protected static final int DEFAULT_LENGTH = 255;

    static {
        typesMapping.put(String.class, "VARCHAR");
        typesMapping.put(char.class, "CHAR");
        typesMapping.put(Integer.class, "INT");
        typesMapping.put(int.class, "INT");
        typesMapping.put(Date.class, "TIMESTAMP");
        typesMapping.put(java.sql.Date.class, "TIMESTAMP");
        typesMapping.put(Short.class, "SMALLINT");
        typesMapping.put(short.class, "SMALLINT");
        typesMapping.put(Byte.class, "TINYINT");
        typesMapping.put(byte.class, "TINYINT");
        typesMapping.put(Long.class, "BIGINT");
        typesMapping.put(long.class, "BIGINT");
        typesMapping.put(Float.class, "FLOAT");
        typesMapping.put(float.class, "FLOAT");

        Field[] fields = Types.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                Integer code = (Integer) field.get(null);
                types.put(code, field.getName());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean isShowSQL = false;

    @Override
    public String getType(int columnTypeInt) {
        return types.get(columnTypeInt);
    }

    @Override
    public String getType(Class clazz) {
        return typesMapping.get(clazz);
    }

    @Override
    public String addColumn(TableModel tableModel, List<ColumnModel> existColumns) {
        String tableName = tableModel.getDbTableName();
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("ALTER TABLE " + tableName);
        for (ColumnModel cm : existColumns) {
            long len = cm.getLength();
            String columnName = cm.getColumnName();
            Class clazz = cm.getJavaType();
            String columnType = typesMapping.get(clazz);
            boolean isPrimaryKey = cm.isPrimaryKey();

            if (isPrimaryKey && cm.isAutoIncrement()) {
                columnType = typesMapping.get(Integer.class);
            }

            if (len <= 0 && clazz.isAssignableFrom(String.class) && !isPrimaryKey) {
                len = DEFAULT_LENGTH;
            }

            String pkString = isPrimaryKey ? " PRIMARY KEY" : "";
            if (isPrimaryKey) {
                if (cm.isAutoIncrement()) {
                    pkString += " AUTO_INCREMENT";
                }
            }

            boolean isAllowNull = cm.isAllowNull();
            if (!isAllowNull) {
                pkString += " NOT NULL ";
                DefaultValue defaultValue = cm.getDefaultValue();
                if (defaultValue != null) {
                    if (defaultValue.enable()) {
                        if (clazz.equals(Integer.class)) {
                            pkString += "DEFAULT " + defaultValue.intValue();
                        }
                        if (clazz.equals(String.class)) {
                            pkString += "DEFAULT '" + defaultValue.stringValue() + "'";
                        }
                    }
                }
            }

            sqlBuilder.append(" ADD ");
            String sql = columnName + " " + columnType + " " + (len > 0 ? "(" + len + ")" : "") + pkString;
            if (CollectionUtils.isLast(existColumns, cm)) {
                sqlBuilder.append(sql);
            } else {
                sqlBuilder.append(sql + ",");
            }
        }
        showSQL(sqlBuilder.toString());
        return sqlBuilder.toString();
    }

    public String createTable(TableModel tableModel) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("CREATE TABLE IF NOT EXISTS " + tableModel.getDbTableName() + "(");
        List<ColumnModel> columnModelList = tableModel.getMappingColumnModels();
        StringBuilder indexSQLBuilder = new StringBuilder();
        StringBuilder primaryKeySQLBuilder = new StringBuilder(",PRIMARY KEY (");
        for (ColumnModel cm : columnModelList) {
            String columnName = cm.getColumnName();
            Class clazz = cm.getJavaType();
            String dbTypeName = typesMapping.get(clazz);
            long length = cm.getLength();
            boolean isPrimaryKey = cm.isPrimaryKey();

            sqlBuilder.append(columnName + " ");
            if (isPrimaryKey && cm.isAutoIncrement()) {
                dbTypeName = typesMapping.get(Integer.class);
            }

            if (length <= 0 && clazz.isAssignableFrom(String.class) && !isPrimaryKey) {
                length = DEFAULT_LENGTH;
            }

            sqlBuilder.append(dbTypeName);
            sqlBuilder.append(length > 0 ? "(" + length + ")" : "");
            if (isPrimaryKey) {
                primaryKeySQLBuilder.append(columnName + ",");
                if (cm.isAutoIncrement()) {
                    sqlBuilder.append(" AUTO_INCREMENT");
                }
            }

            boolean isAllowNull = cm.isAllowNull();
            if (!isAllowNull) {
                sqlBuilder.append(" NOT NULL ");

                DefaultValue defaultValue = cm.getDefaultValue();
                if (defaultValue != null) {
                    if (defaultValue.enable()) {
                        if (clazz.equals(Integer.class)) {
                            sqlBuilder.append("DEFAULT " + defaultValue.intValue());
                        }
                        if (clazz.equals(String.class)) {
                            sqlBuilder.append("DEFAULT '" + defaultValue.stringValue() + "'");
                        }
                    }
                }
            }
            if (CollectionUtils.isLast(columnModelList, cm)) {
                if (cm.isIndex()) {
                    indexSQLBuilder.append(columnName);
                }
            } else {
                sqlBuilder.append(",");
                if (cm.isIndex()) {
                    indexSQLBuilder.append(columnName + ",");
                }
            }
        }
        if (tableModel.haPrimaryKey()) {
            sqlBuilder.append(primaryKeySQLBuilder.substring(0, primaryKeySQLBuilder.length() - 1) + ")");
        }
        if (StringUtils.isNotBlank(indexSQLBuilder.toString())) {
            String indexString = indexSQLBuilder.toString();
            if (indexString.endsWith(",")) {
                indexString = indexString.substring(0, indexString.length() - 1);
            }
            sqlBuilder.append(",INDEX(" + indexString + ")");
        }
        sqlBuilder.append(")");
        showSQL(sqlBuilder.toString());
        return sqlBuilder.toString();
    }

    private boolean contains(Object[] wcs, ColumnModel cm) {
        if (wcs != null && cm != null) {
            for (Object object : wcs) {
                if (String.valueOf(object).equals(cm.getJavaName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ColumnModel> getValidateColumn(TableModel tableMapping, ModelObject object, List<ColumnModel> whereColumns) {
        List<ColumnModel> columnModels = new ArrayList<ColumnModel>();
        object.clearNull();
        Object[] ucs = object.getUpdateColumn();    //主键作为修改内容的数组
        Object[] wcs = object.getWhereColumn();     //作为修改条件的数组
        for (ColumnModel cm : tableMapping.getMappingColumnModels()) {
            if (object.containsKey(cm.getJavaName())) {
                if (whereColumns != null) {
                    if (contains(wcs, cm)) {
                        whereColumns.add(cm);
                    } else {
                        if (!cm.isPrimaryKey()) {
                            columnModels.add(cm);
                        } else {
                            if (contains(ucs, cm)) {
                                columnModels.add(cm);
                            } else {
                                whereColumns.add(cm);
                            }
                        }
                    }
                } else {
                    columnModels.add(cm);
                }
            }
        }
        return columnModels;
    }


    @Override
    public ValuesForPrepared prepareInsert(TableModel tableMapping, ModelObject object) {
        ValuesForPrepared valuesForPrepared = new ValuesForPrepared();
        List<ColumnModel> columnModels = getValidateColumn(tableMapping, object, null);
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();

        Iterator<ColumnModel> it = columnModels.iterator();
        while (it.hasNext()) {
            ColumnModel cm = it.next();
            key.append(cm.getColumnName());
            value.append(":" + cm.getJavaName());
            if (it.hasNext()) {
                key.append(",");
                value.append(",");
            }
            valuesForPrepared.addValue(":" + cm.getJavaName(), object.get(cm.getJavaName()));
        }

        valuesForPrepared.setSql("INSERT INTO " + tableMapping.getDbTableName() + " (" + key + ") VALUES (" + value + ")");
        showSQL(valuesForPrepared.getSql());
        return valuesForPrepared;
    }

    @Override
    public ValuesForPrepared prepareUpdate(TableModel tableMapping, ModelObject object) {
        ValuesForPrepared valuesForPrepared = new ValuesForPrepared();
        List<ColumnModel> whereColumnModels = new ArrayList<ColumnModel>();
        List<ColumnModel> columnModels = getValidateColumn(tableMapping, object, whereColumnModels);

        if (whereColumnModels.size() <= 0) {
            throw new SQLDialectException("update sql no where statement");
        }

        Iterator<ColumnModel> cmIt = columnModels.iterator();
        StringBuilder set = new StringBuilder();
        Iterator<ColumnModel> wcIt = whereColumnModels.iterator();
        StringBuilder where = new StringBuilder();
        while (cmIt.hasNext()) {
            ColumnModel cm = cmIt.next();
            valuesForPrepared.addValue(":" + cm.getJavaName(), object.get(cm.getJavaName()));
            set.append(cm.getColumnName() + "=:" + cm.getJavaName());
            if (cmIt.hasNext()) {
                set.append(" , ");
            }
        }

        while (wcIt.hasNext()) {
            ColumnModel cm = wcIt.next();
            valuesForPrepared.addValue(":" + cm.getJavaName(), object.get(cm.getJavaName()));
            where.append(cm.getColumnName() + "=:" + cm.getJavaName());
            if (wcIt.hasNext()) {
                where.append(" AND ");
            }
        }

        valuesForPrepared.setSql("UPDATE " + tableMapping.getDbTableName() +
                " SET " + set +
                " WHERE " + where);

        showSQL(valuesForPrepared.getSql());

        return valuesForPrepared;
    }

    @Override
    public ValuesForPrepared prepareUpdateBatch(TableModel tableMapping, Batch batch) {
        ValuesForPrepared valuesForPrepared = new ValuesForPrepared();
        List<ColumnModel> whereColumnModels = new ArrayList<ColumnModel>();
        List<ColumnModel> columnModels = new ArrayList<ColumnModel>();

        for (Object object : batch.getColumns()) {
            columnModels.add(tableMapping.getColumnByJavaName(String.valueOf(object)));
        }
        for (Object object : batch.getWhereColumns()) {
            whereColumnModels.add(tableMapping.getColumnByJavaName(String.valueOf(object)));
        }

        Iterator<ColumnModel> cmIt = columnModels.iterator();
        StringBuilder set = new StringBuilder();
        Iterator<ColumnModel> wcIt = whereColumnModels.iterator();
        StringBuilder where = new StringBuilder();
        while (cmIt.hasNext()) {
            ColumnModel cm = cmIt.next();
            set.append(cm.getColumnName() + "=:" + cm.getJavaName());
            if (cmIt.hasNext()) {
                set.append(" , ");
            }
        }

        while (wcIt.hasNext()) {
            ColumnModel cm = wcIt.next();
            where.append(cm.getColumnName() + "=:" + cm.getJavaName());
            if (wcIt.hasNext()) {
                where.append(" AND ");
            }
        }
        valuesForPrepared.setSql("UPDATE " + tableMapping.getDbTableName() + " SET " + set + " WHERE " + where);

        showSQL(valuesForPrepared.getSql());

        return valuesForPrepared;
    }

    private ValuesForPrepared common(TableModel tableMapping, Query query) {
        ValuesForPrepared valuesForPrepared = new ValuesForPrepared();
        String where = " WHERE ";
        StringBuilder sb = new StringBuilder();

        List<Wheres> wheres = query.getWheres();
        Object idValue = query.getIdValue();
        if (idValue != null) {
            List<ColumnModel> columnModels = tableMapping.getMappingPrimaryKeyColumnModels();
            if (columnModels.size() > 0) {
                ColumnModel cm = columnModels.get(0);
                wheres.add(new Wheres(cm.getJavaName(), idValue));
            }
        }
        Iterator<Wheres> wheresIterator = wheres.iterator();
        while (wheresIterator.hasNext()) {
            Wheres whs = wheresIterator.next();
            ColumnModel columnModel = tableMapping.getColumnByJavaName(whs.getKey());

            if (sb.length() != 0) {
                sb.append(" " + whs.getLogic().toString() + " ");
            }
            Wheres.Operation operation = whs.getEnumOperation();
            if (operation.equals(Wheres.Operation.IN)) {
                List<Object> valueList = (List<Object>) whs.getValue();

                Iterator<Object> inIterator = valueList.iterator();
                StringBuilder insb = new StringBuilder();
                int i = 0;
                while (inIterator.hasNext()) {
                    Object object = inIterator.next();
                    insb.append(":" + columnModel.getJavaName() + i);
                    valuesForPrepared.addValue(":" + columnModel.getJavaName() + i, object);
                    if (inIterator.hasNext()) {
                        insb.append(",");
                    }
                    i++;
                }
                sb.append(columnModel.getColumnName() + " IN(" + insb + ")");
            } else if (operation.equals(Wheres.Operation.LIKE)) {
                sb.append(columnModel.getColumnName() + " LIKE(:" + columnModel.getJavaName() + ")");
                valuesForPrepared.addValue(":" + columnModel.getJavaName(), "%" + whs.getValue() + "%");
            } else {
                sb.append(columnModel.getColumnName() + whs.getOperation() + ":" + columnModel.getJavaName());
                valuesForPrepared.addValue(":" + columnModel.getJavaName(), whs.getValue());
            }
        }

        Limit limit = query.getLimit();
        OrderBy orderBy = query.getOrderBy();

        if (orderBy != null) {
            sb.append(" ORDER BY " + orderBy.getField() + " " + orderBy.getType().toString());
        }
        if (limit != null) {
            sb.append(" limit " + limit.getStart() + "," + limit.getLimit());
        }
        if (wheres == null || wheres.size() == 0) {
            valuesForPrepared.setSql(sb.toString());
        } else {
            valuesForPrepared.setSql(where + sb.toString());
        }

        return valuesForPrepared;
    }

    @Override
    public ValuesForPrepared prepareDelete(DBMapping dbMapping, Query query) {
        TableModel tableModel = dbMapping.getTableMapping(query.getObjectClass());
        ValuesForPrepared valuesForPrepared = common(tableModel, query);
        String lastSQLString = valuesForPrepared.getSql();
        if (StringUtils.isBlank(lastSQLString)) {
            throw new SQLDialectException("delete sql must has where");
        }
        valuesForPrepared.setSql("DELETE FROM " + tableModel.getDbTableName() + valuesForPrepared.getSql());

        showSQL(valuesForPrepared.getSql());

        return valuesForPrepared;
    }

    @Override
    public ValuesForPrepared prepareSelect(DBMapping dbMapping, Query query) {
        TableModel tableMapping = dbMapping.getTableMapping(query.getObjectClass());
        ValuesForPrepared valuesForPrepared = common(tableMapping, query);
        String lastSQLString = valuesForPrepared.getSql();
        valuesForPrepared.setSql("SELECT * FROM " + tableMapping.getDbTableName() + lastSQLString);

        showSQL(valuesForPrepared.getSql());

        return valuesForPrepared;
    }

    @Override
    public ValuesForPrepared prepareSelectCount(TableModel tableMapping, Query query) {
        ValuesForPrepared valuesForPrepared = common(tableMapping, query);
        valuesForPrepared.setSql("SELECT COUNT(*) FROM " + tableMapping.getDbTableName() + valuesForPrepared.getSql());

        showSQL(valuesForPrepared.getSql());

        return valuesForPrepared;
    }

    protected void showSQL(String sql) {
        if (this.isShowSQL) {
            logger.info(sql);
        }
    }

    @Override
    public void setShowSQL(boolean isShowSQL) {
        this.isShowSQL = isShowSQL;
    }
}
