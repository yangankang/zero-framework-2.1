package com.yoosal.orm.core;

import com.yoosal.orm.ModelObject;
import com.yoosal.orm.OperationManager;
import com.yoosal.orm.mapping.DBMapping;
import com.yoosal.orm.query.Query;

import java.sql.SQLException;
import java.util.List;

public class SessionOperationManager implements Operation {
    public static ThreadLocal<SessionOperation> threadLocal = new ThreadLocal();
    private static DBMapping mapping = OperationManager.getMapping();

    @Override
    public void begin() throws SQLException {
        getOperation().begin();
    }

    private SessionOperation getOperation() {
        if (threadLocal.get() == null) {
            try {
                SessionOperation sessionOperation = new OrmSessionOperation(OperationManager.getDataSourceManager());
                sessionOperation.setDbMapping(mapping);
                threadLocal.set(sessionOperation);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
        return threadLocal.get();
    }

    @Override
    public ModelObject save(ModelObject object) {
        try {
            return getOperation().save(object);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public void update(ModelObject object) {
        try {
            getOperation().update(object);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public void updates(Batch batch) {
        try {
            getOperation().updates(batch);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public void remove(Query query) {
        try {
            getOperation().remove(query);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public List<ModelObject> list(Query query) {
        try {
            return getOperation().list(query);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public ModelObject query(Query query) {
        try {
            return getOperation().query(query);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public long count(Query query) {
        try {
            return getOperation().count(query);
        } finally {
            getOperation().close();
        }
    }

    @Override
    public void commit() throws SQLException {
        getOperation().commit();
    }

    @Override
    public void rollback() {
        getOperation().rollback();
    }
}
