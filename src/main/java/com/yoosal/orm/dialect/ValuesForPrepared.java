package com.yoosal.orm.dialect;

import com.yoosal.orm.ModelObject;
import org.apache.commons.collections.map.HashedMap;

import java.sql.*;
import java.util.*;

public class ValuesForPrepared {
    private String sql;
    private String[] keys;
    private Map<String, Object> values = new HashMap<String, Object>();
    private CreatorJoinModel model;

    public String getSql() {
        String replaceSQL = sql;
        String[] strings = getKeys();
        for (int i = 0; i < strings.length; i++) {
            String s = strings[i];
            replaceSQL = replaceSQL.replaceFirst(":" + s, "?");
        }
        return replaceSQL;
    }

    public void setPrepared(PreparedStatement statement) throws SQLException {
        String[] strings = getKeys();
        for (int i = 0; i < strings.length; i++) {
            String s = strings[i];
            Object o = values.get(":" + s);
            statement.setObject(i + 1, o);
        }
    }

    public void setPrepared(PreparedStatement statement, ModelObject object) throws SQLException {
        String[] strings = getKeys();
        for (int i = 0; i < strings.length; i++) {
            String s = strings[i];
            statement.setObject(i + 1, object.get(s));
        }
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String[] getKeys() {
        if (keys == null) {
            keys = wildcardString();
        }
        return keys;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public Map<String, Object> getKeyValues() {
        getKeys();
        Map<String, Object> m = new HashedMap();
        for (String k : keys) {
            m.put(k, values.get(":" + k));
        }
        return m;
    }

    public void addValue(String key, Object value) {
        values.put(key, value);
    }

    public CreatorJoinModel getModel() {
        return model;
    }

    public void setModel(CreatorJoinModel model) {
        this.model = model;
    }

    private String[] wildcardString() {
        String[] s1 = sql.split(":");
        List<String> strings = new ArrayList<String>();
        for (String s : s1) {
            if (s1[0] == s) {
                continue;
            }
            String[] s2 = s.split(":");
            for (String s3 : s2) {
                char[] chars = s3.toCharArray();
                int i = 0;
                StringBuilder sb = new StringBuilder();
                while (true) {
                    if (i >= chars.length) {
                        break;
                    }
                    if ((chars[i] >= 'a' && chars[i] <= 'z') ||
                            (chars[i] >= 'A' && chars[i] <= 'Z')
                            || chars[i] == '_'
                            || chars[i] == '.'
                            || Character.isDigit(chars[i])
                            || chars[i] == '#') {
                        sb.append(chars[i]);
                    } else {
                        break;
                    }
                    i++;
                }
                strings.add(sb.toString());
            }
        }
        String[] s = new String[strings.size()];
        strings.toArray(s);
        return s;
    }
}
