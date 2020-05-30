/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression.function;

import org.h2.command.Parser;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.expression.Expression;
import org.h2.expression.ExpressionColumn;
import org.h2.expression.ExpressionVisitor;
import org.h2.expression.Operation1_2;
import org.h2.message.DbException;
import org.h2.schema.Schema;
import org.h2.schema.Sequence;
import org.h2.util.StringUtils;
import org.h2.value.TypeInfo;
import org.h2.value.Value;

/**
 * NEXTVAL() and CURRVAL() compatibility functions.
 */
public class CompatibilitySequenceValueFunction extends Operation1_2 implements NamedExpression {

    private final boolean current;

    public CompatibilitySequenceValueFunction(Expression left, Expression right, boolean current) {
        super(left, right);
        this.current = current;
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, int sqlFlags) {
        left.getSQL(builder.append(getName()).append('('), sqlFlags);
        if (right != null) {
            right.getSQL(builder.append(", "), sqlFlags);
        }
        return builder.append(')');
    }

    @Override
    public Value getValue(Session session) {
        Value v0 = left.getValue(session);
        String schemaName, sequenceName;
        if (right == null) {
            Parser p = new Parser(session);
            String sql = v0.getString();
            Expression expr = p.parseExpression(sql);
            if (expr instanceof ExpressionColumn) {
                ExpressionColumn seq = (ExpressionColumn) expr;
                schemaName = seq.getOriginalTableAliasName();
                if (schemaName == null) {
                    schemaName = session.getCurrentSchemaName();
                    sequenceName = sql;
                } else {
                    sequenceName = seq.getColumnName(session, -1);
                }
            } else {
                throw DbException.getSyntaxError(sql, 1);
            }
        } else {
            schemaName = v0.getString();
            sequenceName = right.getValue(session).getString();
        }
        Database database = session.getDatabase();
        Schema s = database.findSchema(schemaName);
        if (s == null) {
            schemaName = StringUtils.toUpperEnglish(schemaName);
            s = database.getSchema(schemaName);
        }
        Sequence seq = s.findSequence(sequenceName);
        if (seq == null) {
            sequenceName = StringUtils.toUpperEnglish(sequenceName);
            seq = s.getSequence(sequenceName);
        }
        return (current ? session.getCurrentValueFor(seq) : session.getNextValueFor(seq, null)).convertTo(type);
    }

    @Override
    public Expression optimize(Session session) {
        left = left.optimize(session);
        if (right != null) {
            right = right.optimize(session);
        }
        type = session.getMode().decimalSequences ? TypeInfo.TYPE_NUMERIC_BIGINT : TypeInfo.TYPE_BIGINT;
        return this;
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        switch (visitor.getType()) {
        case ExpressionVisitor.INDEPENDENT:
        case ExpressionVisitor.DETERMINISTIC:
        case ExpressionVisitor.QUERY_COMPARABLE:
            return false;
        case ExpressionVisitor.READONLY:
            if (!current) {
                return false;
            }
            //$FALL-THROUGH$
        case ExpressionVisitor.OPTIMIZABLE_AGGREGATE:
        case ExpressionVisitor.EVALUATABLE:
        case ExpressionVisitor.SET_MAX_DATA_MODIFICATION_ID:
        case ExpressionVisitor.NOT_FROM_RESOLVER:
        case ExpressionVisitor.GET_DEPENDENCIES:
        case ExpressionVisitor.GET_COLUMNS1:
        case ExpressionVisitor.GET_COLUMNS2:
            return super.isEverything(visitor);
        default:
            throw DbException.throwInternalError("type=" + visitor.getType());
        }
    }

    @Override
    public String getName() {
        return current ? "CURRVAL" : "NEXTVAL";
    }

}
