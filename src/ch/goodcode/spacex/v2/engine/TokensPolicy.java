/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.goodcode.spacex.v2.engine;

/**
 *
 * @author Paolo Domenighetti
 */
public class TokensPolicy {

    private final int tokens;
    private final String fieldType;
    private final String fieldName;

    public TokensPolicy(int tokens, String fieldType, String fieldName) {
        this.tokens = tokens;
        this.fieldType = fieldType;
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public int getTokens() {
        return tokens;
    }

    public String getFieldType() {
        return fieldType;
    }

}
