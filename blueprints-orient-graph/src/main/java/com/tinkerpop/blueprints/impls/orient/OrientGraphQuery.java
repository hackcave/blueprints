package com.tinkerpop.blueprints.impls.orient;

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Query;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.DefaultGraphQuery;

import java.util.Collections;

/**
 * OrientDB implementation for Graph query.
 *
 * @author Luca Garulli (http://www.orientechnologies.com)
 */
public class OrientGraphQuery extends DefaultGraphQuery {

    protected static final char SPACE = ' ';
    protected static final String OPERATOR_DIFFERENT = "<>";
    protected static final String OPERATOR_IS_NOT = "is not";
    protected static final String OPERATOR_LET = "<=";
    protected static final char OPERATOR_LT = '<';
    protected static final String OPERATOR_GTE = ">=";
    protected static final char OPERATOR_GT = '>';
    protected static final String OPERATOR_EQUALS = "=";
    protected static final String OPERATOR_IS = "is";
    protected static final String OPERATOR_IN = " in ";

    protected static final String QUERY_FILTER_AND = " and ";
    protected static final String QUERY_FILTER_OR = " or ";
    protected static final char QUERY_STRING = '\'';
    protected static final char QUERY_SEPARATOR = ',';
    protected static final char COLLECTION_BEGIN = '[';
    protected static final char COLLECTION_END = ']';
    protected static final char PARENTHESIS_BEGIN = '(';
    protected static final char PARENTHESIS_END = ')';
    protected static final String QUERY_LABEL_BEGIN = " and label in [";
    protected static final String QUERY_LABEL_END = "]";
    protected static final String QUERY_WHERE = " where 1=1";
    protected static final String QUERY_SELECT_FROM = "select from ";
    protected static final String LIMIT = " LIMIT ";
    //protected static final String SKIP = " SKIP ";
    
    protected String fetchPlan;

    public OrientGraphQuery(final Graph iGraph) {
        super(iGraph);
    }

    public Query labels(final String... labels) {
        this.labels = labels;
        return this;
    }

    @Override
    public Iterable<Vertex> vertices() {
        if (limit == 0)
            return Collections.emptyList();

        final StringBuilder text = new StringBuilder();

        // GO DIRECTLY AGAINST E CLASS AND SUB-CLASSES
        text.append(QUERY_SELECT_FROM);

        if (((OrientBaseGraph) graph).isUseClassForVertexLabel()
                && labels != null && labels.length > 0) {
            // FILTER PER CLASS SAVING CHECKING OF LABEL PROPERTY
            if (labels.length == 1)
                // USE THE CLASS NAME
                text.append(OrientBaseGraph.encodeClassName(labels[0]));
            else {
                // MULTIPLE CLASSES NOT SUPPORTED DIRECTLY: CREATE A SUB-QUERY
                return super.vertices();
            }
        } else
            text.append(OrientVertex.CLASS_NAME);

        // APPEND ALWAYS WHERE 1=1 TO MAKE CONCATENATING EASIER
        text.append(QUERY_WHERE);
        manageFilters(text);
        if (!((OrientBaseGraph) graph).isUseClassForVertexLabel())
            manageLabels(text);

        if (limit > 0 && limit < Long.MAX_VALUE) {

                text.append(LIMIT);
                text.append(limit);

        }
        final OSQLSynchQuery<OIdentifiable> query = new OSQLSynchQuery<OIdentifiable>(
                text.toString());
        
        if( fetchPlan != null )
        	query.setFetchPlan(fetchPlan);
        
        return new OrientElementIterable<Vertex>(((OrientBaseGraph) graph),
                ((OrientBaseGraph) graph).getRawGraph().query(query));
    }

    @Override
    public Iterable<Edge> edges() {
        if (limit == 0)
            return Collections.emptyList();

        if (((OrientBaseGraph) graph).isUseLightweightEdges())
            return super.edges();

        final StringBuilder text = new StringBuilder();

        // GO DIRECTLY AGAINST E CLASS AND SUB-CLASSES
        text.append(QUERY_SELECT_FROM);

        if (((OrientBaseGraph) graph).isUseClassForEdgeLabel()
                && labels != null && labels.length > 0) {
            // FILTER PER CLASS SAVING CHECKING OF LABEL PROPERTY
            if (labels.length == 1)
                // USE THE CLASS NAME
                text.append(OrientBaseGraph.encodeClassName(labels[0]));
            else {
                // MULTIPLE CLASSES NOT SUPPORTED DIRECTLY: CREATE A SUB-QUERY
                return super.edges();
            }
        } else
            text.append(OrientEdge.CLASS_NAME);

        // APPEND ALWAYS WHERE 1=1 TO MAKE CONCATENATING EASIER
        text.append(QUERY_WHERE);

        manageFilters(text);
        if (!((OrientBaseGraph) graph).isUseClassForEdgeLabel())
            manageLabels(text);

        final OSQLSynchQuery<OIdentifiable> query = new OSQLSynchQuery<OIdentifiable>(
                text.toString());

        if( fetchPlan != null )
        	query.setFetchPlan(fetchPlan);
        
        if (limit > 0 && limit < Long.MAX_VALUE)
            query.setLimit((int) limit);

        return new OrientElementIterable<Edge>(((OrientBaseGraph) graph),
                ((OrientBaseGraph) graph).getRawGraph().query(query));
    }

	public String getFetchPlan() {
		return fetchPlan;
	}

	public void setFetchPlan(final String fetchPlan) {
		this.fetchPlan = fetchPlan;
	}
	
    protected void manageLabels(final StringBuilder text) {
        if (labels != null && labels.length > 0) {
            // APPEND LABELS
            text.append(QUERY_LABEL_BEGIN);
            for (int i = 0; i < labels.length; ++i) {
                if (i > 0)
                    text.append(QUERY_SEPARATOR);
                text.append(QUERY_STRING);
                text.append(labels[i]);
                text.append(QUERY_STRING);
            }
            text.append(QUERY_LABEL_END);
        }
    }

    protected void manageFilters(final StringBuilder text) {
        for (HasContainer has : hasContainers) {
            text.append(QUERY_FILTER_AND);

            if (has.compare == com.tinkerpop.blueprints.Compare.EQUAL && has.values.length > 1) {
                // IN
                text.append(has.key);
                text.append(OPERATOR_IN);
                text.append(COLLECTION_BEGIN);

                for (int i = 0; i < has.values.length; ++i) {
                    if (i > 0)
                        text.append(QUERY_SEPARATOR);
                    generateFilterValue(text, has.values[i]);
                }

                text.append(COLLECTION_END);
            } else {
                // ANY OTHER OPERATORS
                if (has.values.length > 1)
                    text.append(PARENTHESIS_BEGIN);

                for (int i = 0; i < has.values.length; ++i) {
                    if (i > 0) {
                        text.append(SPACE);
                        text.append(QUERY_FILTER_OR);
                    }

                    text.append(has.key);
                    text.append(SPACE);

                    switch (has.compare) {
                        case EQUAL:
                            if (has.values[0] == null)
                                // IS
                                text.append(OPERATOR_IS);
                            else
                                // EQUALS
                                text.append(OPERATOR_EQUALS);
                            break;
                        case GREATER_THAN:
                            text.append(OPERATOR_GT);
                            break;
                        case GREATER_THAN_EQUAL:
                            text.append(OPERATOR_GTE);
                            break;
                        case LESS_THAN:
                            text.append(OPERATOR_LT);
                            break;
                        case LESS_THAN_EQUAL:
                            text.append(OPERATOR_LET);
                            break;
                        case NOT_EQUAL:
                            if (has.values[0] == null)
                                text.append(OPERATOR_IS_NOT);
                            else
                                text.append(OPERATOR_DIFFERENT);
                            break;
                    }
                    text.append(SPACE);
                    generateFilterValue(text, has.values[i]);
                }

                if (has.values.length > 1)
                    text.append(PARENTHESIS_END);
            }
        }
    }

    protected void generateFilterValue(final StringBuilder text, final Object iValue) {
        if (iValue instanceof String)
            text.append(QUERY_STRING);
        text.append(iValue);
        if (iValue instanceof String)
            text.append(QUERY_STRING);
    }
}