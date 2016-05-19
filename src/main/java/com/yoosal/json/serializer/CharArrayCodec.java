package com.yoosal.json.serializer;

import java.lang.reflect.Type;

import com.yoosal.json.JSON;
import com.yoosal.json.parser.DefaultJSONParser;
import com.yoosal.json.parser.JSONLexer;
import com.yoosal.json.parser.JSONToken;
import com.yoosal.json.parser.deserializer.ObjectDeserializer;


public class CharArrayCodec implements ObjectDeserializer {

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        return (T) deserialze(parser);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            String val = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toCharArray();
        }
        
        if (lexer.token() == JSONToken.LITERAL_INT) {
            Number val = lexer.integerValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val.toString().toCharArray();
        }

        Object value = parser.parse();
        return value == null //
            ? null //
            : (T) JSON.toJSONString(value).toCharArray();
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }
}
