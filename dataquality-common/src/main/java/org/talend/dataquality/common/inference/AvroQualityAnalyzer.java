package org.talend.dataquality.common.inference;

import org.apache.avro.Schema;
import org.talend.dataquality.common.exception.DQCommonRuntimeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.talend.dataquality.common.util.AvroUtils.itemId;

public abstract class AvroQualityAnalyzer implements AvroAnalyzer {

    private static final long serialVersionUID = 7878661383777406934L;

    public static final String GLOBAL_QUALITY_PROP_NAME = "talend.component.globalQuality";

    public static final String QUALITY_PROP_NAME = "talend.component.qualityAggregate";

    public static final String DQTYPE_PROP_NAME = "talend.component.dqType";

    public static final String DQTYPE_DATATYPE_FIELD_NAME = "dataType";

    public static final String DQTYPE_DQTYPE_FIELD_NAME = "dqType";

    public static final String VALIDITY_FIELD_NAME = "validity";

    public static final int VALID_VALUE = 1;

    public static final int INVALID_VALUE = -1;

    public static final int EMPTY_VALUE = 0;

    protected boolean isStoreInvalidValues = true;

    protected final Map<String, ValueQualityStatistics> qualityResults = new HashMap<>();

    /** Semantic schema with data about discovered types. */
    protected Schema semanticSchema;

    /** Schema with the global quality. */
    protected Schema globalQualitySchema;

    /** Schema for the records containing the results of the analysis of each field. */
    protected Schema recordQualitySchema;

    protected <T> T getOrCreate(String key, Map<String, T> map, Class<T> itemClass) {
        T value = map.get(key);

        if (value == null) {
            try {
                value = itemClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new DQCommonRuntimeException("Unable to get create an instance of " + itemClass, e);
            }

            map.put(key, value);
        }

        return value;
    }

    @Override
    public Schema getResult() {
        if (semanticSchema == null) {
            return null;
        }

        final ValueQualityStatistics stats = qualityResults.values().stream().reduce(new ValueQualityStatistics(),
                ValueQualityStatistics::mergeCounts);

        // Create a deep copy of the semantic schema (without "talend.component.dqType")
        // Update it with the information about the quality

        globalQualitySchema.addProp(GLOBAL_QUALITY_PROP_NAME, stats.toMap());

        for (Schema.Field field : globalQualitySchema.getFields()) {
            updateQuality(field.schema(), field.name());
        }

        return globalQualitySchema;
    }

    private Map<String, Long> getStatMap(String resultKey) {
        if (qualityResults.containsKey(resultKey)) {
            return qualityResults.get(resultKey).toMap();
        }

        return new ValueQualityStatistics().toMap();
    }

    private Schema updateQuality(Schema sourceSchema, String prefix) {
        switch (sourceSchema.getType()) {
        case RECORD:
            for (Schema.Field field : sourceSchema.getFields()) {
                updateQuality(field.schema(), itemId(prefix, field.name()));
            }
            break;

        case UNION:
            if (qualityResults.containsKey(prefix)) {
                sourceSchema.addProp(QUALITY_PROP_NAME, getStatMap(prefix));
            }
            for (Schema unionSchema : sourceSchema.getTypes()) {
                updateQuality(unionSchema, itemId(prefix, unionSchema.getName()));
            }
            break;

        case ARRAY:
        case MAP:
        case ENUM:
        case FIXED:
        case STRING:
        case BYTES:
        case INT:
        case LONG:
        case FLOAT:
        case DOUBLE:
        case BOOLEAN:
        case NULL:
            sourceSchema.addProp(QUALITY_PROP_NAME, getStatMap(prefix));
            break;
        }

        return null;
    }

    @Override
    public List<Schema> getResults() {
        return Collections.singletonList(getResult());
    }

    @Override
    public void close() throws Exception {
        // Nothing to do
    }
}