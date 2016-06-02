package com.yoosal.orm.mapping;

import com.yoosal.orm.annotation.Column;
import com.yoosal.orm.core.IDStrategy;
import com.yoosal.orm.exception.OrmMappingException;

public class ColumnModel extends AbstractModelCheck {
    private String javaName;
    private Class javaType;
    private int code;   //排序的数值

    private String columnName;
    private String columnType;
    private int columnTypeCode;
    private long length;
    private Class<IDStrategy> generateStrategy;
    private IDStrategy generateStrategyInstance;

    private boolean isPrimaryKey;
    private boolean isLock = false;
    private boolean isIndex = false;
    private String indexName;

    public boolean isIndex() {
        return isIndex;
    }

    public void setIndex(boolean index) {
        isIndex = index;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getJavaName() {
        return javaName;
    }

    public void setJavaName(String javaName) {
        this.javaName = javaName;
    }

    public Class getJavaType() {
        return javaType;
    }

    public void setJavaType(Class javaType) {
        this.javaType = javaType;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getColumnType() {
        return columnType;
    }

    public void setColumnType(String columnType) {
        this.columnType = columnType;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public Class getGenerateStrategy() {
        return generateStrategy;
    }

    public void setGenerateStrategy(Class<IDStrategy> generateStrategy) {
        this.generateStrategy = generateStrategy;
        try {
            generateStrategyInstance = generateStrategy.newInstance();
        } catch (InstantiationException e) {
            throw new OrmMappingException("instance generateStrategy class throw", e);
        } catch (IllegalAccessException e) {
            throw new OrmMappingException("instance generateStrategy class throw", e);
        }
    }

    public IDStrategy getIDStrategy() {
        return generateStrategyInstance;
    }

    public int getColumnTypeCode() {
        return columnTypeCode;
    }

    public void setColumnTypeCode(int columnTypeCode) {
        this.columnTypeCode = columnTypeCode;
    }

    public boolean isPrimaryKey() {
        return isPrimaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        isPrimaryKey = primaryKey;
    }

    public boolean isLock() {
        return isLock;
    }

    public void setLock(boolean lock) {
        isLock = lock;
    }

    @Override
    protected String getName() {
        return this.javaName;
    }

    @Override
    protected void setMappingName(String name) {
        this.columnName = name;
    }

    public boolean isAutoIncrement() {
        if (this.isPrimaryKey() && generateStrategy.equals(Column.class)) {
            return true;
        }
        return false;
    }
}
