// ============================================================================
//
// Copyright (C) 2006-2021 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.dataquality.matchmerge.mfb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.dataquality.matchmerge.MatchMergeAlgorithm;
import org.talend.dataquality.matchmerge.Record;
import org.talend.dataquality.matchmerge.SubString;
import org.talend.dataquality.record.linkage.attribute.AttributeMatcherFactory;
import org.talend.dataquality.record.linkage.attribute.IAttributeMatcher;
import org.talend.dataquality.record.linkage.constant.AttributeMatcherType;
import org.talend.dataquality.record.linkage.exception.DQRecordLinkageRuntimeException;
import org.talend.dataquality.record.linkage.record.IRecordMatcher;
import org.talend.dataquality.record.linkage.record.IRecordMerger;
import org.talend.dataquality.record.linkage.utils.SurvivorShipAlgorithmEnum;

public class MFB implements MatchMergeAlgorithm {

    private static final Logger LOGGER = LoggerFactory.getLogger(MFB.class);

    protected final IRecordMatcher matcher;

    private final IRecordMerger merger;

    private boolean isHandleGoldenRecord = false;

    // Store the mapping golden record id and golden record
    protected Map<String, Record> goldenRecordMapExist = new HashMap<>();

    // Store the golden record id and the list which need to do match with it
    protected Map<String, List<Record>> goldenRecordMapUnExist = new HashMap<>();

    /**
     * Builds a Swoosh implementation based on a {@link org.talend.dataquality.record.linkage.record.IRecordMatcher
     * matcher} and a {@link org.talend.dataquality.record.linkage.record.IRecordMerger merger}.
     * 
     * @param matcher A matcher to be used to compare records together.
     * @param merger A merger to be used to create a merged (a.k.a "golden") record.
     * @see #execute(java.util.Iterator)
     */
    public MFB(IRecordMatcher matcher, IRecordMerger merger) {
        if (matcher == null) {
            throw new IllegalArgumentException("Matcher cannot be null."); //$NON-NLS-1$
        }
        if (merger == null) {
            throw new IllegalArgumentException("Merger cannot be null."); //$NON-NLS-1$
        }
        this.matcher = matcher;
        this.merger = merger;
    }

    /**
     * Builds a Swoosh implementation based on provided parameters. This builder is
     * 
     * @param algorithms Types of algorithm to use for match ordered by position of field.
     * @param algorithmParameters Parameter for nth match algorithm (or null if N/A).
     * @param thresholds Threshold for the nth match algorithm (consider the nth column as a match if match is greater
     * than or equals the threshold).
     * @param minConfidenceValue The minimum confidence in the final (merged) record.
     * @param merges The algorithms to use for merging records.
     * @param mergesParameters Parameter for nth merge algorithm (or null if N/A).
     * @param weights Indicates weight for the nth match algorithm.
     * @param nullOptions Indicates how Swoosh should handle <code>null</code> values for nth field.
     * @param subStrings Indicates if Swoosh should perform any substring operation before comparison.
     * @param mergedRecordSource Indicate what should be the
     * {@link org.talend.dataquality.matchmerge.Record#getSource() source} of merged records.
     * @return A {@link org.talend.dataquality.matchmerge.MatchMergeAlgorithm} implementation ready for usage.
     * @see org.talend.dataquality.matchmerge.MatchMergeAlgorithm#execute(java.util.Iterator)
     */
    public static MFB build(AttributeMatcherType[] algorithms, String[] algorithmParameters, float[] thresholds,
            double minConfidenceValue, SurvivorShipAlgorithmEnum[] merges, String[] mergesParameters, double[] weights,
            IAttributeMatcher.NullOption[] nullOptions, SubString[] subStrings, String mergedRecordSource) {
        MFBRecordMatcher newMatcher = new MFBRecordMatcher(minConfidenceValue);
        newMatcher.setRecordSize(algorithms.length);
        // Create attribute match
        IAttributeMatcher[] attributeMatchers = new IAttributeMatcher[algorithms.length];
        int i = 0;
        for (AttributeMatcherType algorithm : algorithms) {
            IAttributeMatcher attributeMatcher;
            if (algorithm != AttributeMatcherType.CUSTOM) {
                attributeMatcher = AttributeMatcherFactory.createMatcher(algorithm);
            } else {
                try {
                    attributeMatcher = AttributeMatcherFactory.createMatcher(algorithm, algorithmParameters[i]);
                } catch (Exception e) {
                    throw new DQRecordLinkageRuntimeException(
                            "Could not instantiate match class '" + algorithmParameters[i] + "'.", e); //$NON-NLS-1$//$NON-NLS-2$
                }
            }
            attributeMatcher.setNullOption(nullOptions[i]); // Null handling
            attributeMatchers[i] = MFBAttributeMatcher.wrap(attributeMatcher, weights[i], thresholds[i], subStrings[i]);
            i++;
        }
        newMatcher.setAttributeMatchers(attributeMatchers);
        // Set minimum confidence
        newMatcher.setRecordMatchThreshold(minConfidenceValue);
        // Attribute weights
        newMatcher.setAttributeWeights(weights);
        //Survivorship function , TDQ-18347 
        newMatcher.setSurvivorShipFunction(merges);
        // Create MFB instance
        return new MFB(newMatcher, new MFBRecordMerger(mergedRecordSource, mergesParameters, merges));
    }

    @Override
    public List<Record> execute(Iterator<Record> sourceRecords) {
        return execute(sourceRecords, DefaultCallback.INSTANCE);
    }

    @Override
    public List<Record> execute(Iterator<Record> sourceRecords, Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null."); //$NON-NLS-1$
        }
        List<Record> mergedRecords = new ArrayList<Record>();
        int index = 0;
        // Read source record per record
        Queue<Record> queue = new ProcessQueue<Record>(sourceRecords);
        callback.onBeginProcessing();
        while (!queue.isEmpty() && !callback.isInterrupted()) {
            if (LOGGER.isDebugEnabled() && index % 10000 == 0) {
                LOGGER.debug("Current index: " + index); //$NON-NLS-1$
            }
            execute(queue.poll(), mergedRecords, queue, callback);
            index++;
        }
        // In case callback asked for interruption, dumps all merged records to results (in case callback interrupted
        // because it decided there would no longer be any interesting result).
        if (callback.isInterrupted()) {
            while (!queue.isEmpty()) {
                Record record = queue.poll();
                if (record.getRelatedIds().size() > 1) {
                    mergedRecords.add(record);
                }
            }
        }
        // Post merge processing (most common values...)
        callback.onEndProcessing();
        return mergedRecords;
    }

    /**
     * <p>
     * Performs match & merge operation on <code>record</code>. For algorithm purposes, the list of previously merged
     * records (<code>mergedRecords</code>) as well as the queue of records (<code>queue</code>) to be processed are
     * needed.
     * </p>
     * <p>
     * A <code>callback</code> is used to notify calling code about decisions the processing does.
     * </p>
     * 
     * @param record The record to be compared to previously merged records.
     * @param mergedRecords The previously merged records.
     * @param queue Queue of records to be processed: in case of new merge, algorithm is expected to publish new merged
     * record on this queue for later processing.
     * @param callback A {@link org.talend.dataquality.matchmerge.MatchMergeAlgorithm.Callback} to notify callers of
     * decisions the algorithm takes.
     */
    protected void execute(Record record, List<Record> mergedRecords, Queue<Record> queue, Callback callback) {
        callback.onBeginRecord(record);
        // Sanity checks
        if (record == null) {
            throw new IllegalArgumentException("Record cannot be null."); //$NON-NLS-1$
        }
        // MFB algorithm
        boolean hasCreatedNewMerge = false;
        if (!checkRecordValidation(record)) {
            return;
        }
        for (Record mergedRecord : mergedRecords) {
            MatchResult matchResult = doMatch(mergedRecord, record);
            if (matchResult.isMatch()) {
                // need get a map list from the matchResult if one of record is golden record and they can match
                executeSubMatchForGoldenRecord(record, mergedRecords, queue, callback, mergedRecord, matchResult);
                matchTwoRecord(record, mergedRecords, queue, callback, mergedRecord, matchResult);
                hasCreatedNewMerge = true;
                break;
            } else {
                callback.onDifferent(mergedRecord, record, matchResult);
            }
        }
        hasCreatedNewMerge = startDelayMatch(record, mergedRecords, queue, callback, hasCreatedNewMerge);
        if (!hasCreatedNewMerge) {
            record.getRelatedIds().add(record.getId());
            mergedRecords.add(record);
            callback.onNewMerge(record);
        }
        callback.onEndRecord(record);
    }

    /**
     * Sometimes new record need to match with other golden reocrd but it is not here on the time. So that we call this
     * method to check it here or not
     */
    protected boolean startDelayMatch(Record record, List<Record> mergedRecords, Queue<Record> queue, Callback callback,
            boolean hasCreatedNewMerge) {
        return hasCreatedNewMerge;
    }

    protected boolean checkRecordValidation(Record record) {
        return true;
    }

    /**
     * Sometimes new record need to match with other golden reocrd
     */
    protected void executeSubMatchForGoldenRecord(Record record, List<Record> mergedRecords, Queue<Record> queue,
            Callback callback, Record mergedRecord, MatchResult matchResult) {
        // nothing need to do here

    }

    protected void matchTwoRecord(Record record, List<Record> mergedRecords, Queue<Record> queue, Callback callback,
            Record mergedRecord, MatchResult matchResult) {
        callback.onMatch(mergedRecord, record, matchResult);
        Record newMergedRecord = merger.merge(record, mergedRecord);
        synGoldenRecordMap(newMergedRecord, record, mergedRecord);
        callback.onSynResult(newMergedRecord, record, matchResult);
        queue.offer(newMergedRecord);
        callback.onNewMerge(newMergedRecord);
        mergedRecords.remove(mergedRecord);
        callback.onRemoveMerge(mergedRecord);
    }

    /**
     * syn goldenRecordMapExist and goldenRecordMapUnExist map here after match two record
     */
    protected void synGoldenRecordMap(Record newMergedRecord, Record record, Record mergedRecord) {
        // nothing to do here
    }

    protected MatchResult doMatch(Record leftRecord, Record rightRecord) {
        if (leftRecord.getAttributes().size() != rightRecord.getAttributes().size()) {
            throw new IllegalArgumentException("Records do not share same attribute count."); //$NON-NLS-1$
        }
        if (leftRecord.getGroupId() != null && rightRecord.getGroupId() != null) {
            if (!leftRecord.getGroupId().equals(rightRecord.getGroupId())) {
                boolean isMatchDiffGroup = isMatchDiffGroups();
                if (!isMatchDiffGroup) {
                    return NonMatchResult.wrap(matcher.getMatchingWeight(leftRecord, rightRecord));
                }
            } else { // Two records of same group
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Merging already merged records (same group id)."); //$NON-NLS-1$
                }
            }
        }

        MatchResult matchResult = matcher.getMatchingWeight(leftRecord, rightRecord);
        matchResult = maskImpactMatchMap(leftRecord, rightRecord, matchResult);
        // Build match result
        return matchResult;
    }

    protected MatchResult maskImpactMatchMap(Record leftRecord, Record rightRecord, MatchResult matchResult) {
        return matchResult;
    }

    protected boolean isGoldenRecordCase(Record leftRecord, Record rightRecord) {
        return leftRecord.matchInForbiddenList(rightRecord);
    }

    protected boolean isHandleGoldenRecord() {
        return isHandleGoldenRecord;
    }

    public void setHandleGoldenRecord(boolean isHandleGoldenRecord) {
        this.isHandleGoldenRecord = isHandleGoldenRecord;
    }

    /**
     * @return <code>true</code> if algorithm should try to match records from different groups.
     */
    protected boolean isMatchDiffGroups() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Cannot match record: already different groups."); //$NON-NLS-1$
        }
        return false;
    }

    @Override
    public IRecordMatcher getMatcher() {
        return matcher;
    }

    public IRecordMerger getMerger() {
        return merger;
    }

    public static class NonMatchResult extends MatchResult {

        public static final MatchResult INSTANCE = wrap(new MatchResult(0));

        private final MatchResult result;

        private NonMatchResult(MatchResult result) {
            super(result.getScores().size());
            this.result = result;
        }

        public static MatchResult wrap(MatchResult result) {
            return new NonMatchResult(result);
        }

        @Override
        public List<Score> getScores() {
            return result.getScores();
        }

        @Override
        public List<Float> getThresholds() {
            return result.getThresholds();
        }

        @Override
        public void setScore(int index, AttributeMatcherType algorithm, double score, String recordId1, String value1,
                String recordId2, String value2) {
            result.setScore(index, algorithm, score, recordId1, value1, recordId2, value2);
        }

        @Override
        public void setThreshold(int index, float threshold) {
            result.setThreshold(index, threshold);
        }

        @Override
        public boolean isMatch() {
            return false;
        }
    }
}
