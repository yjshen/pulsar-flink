/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.streaming.connectors.pulsar.internal;

import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks;
import org.apache.flink.streaming.api.watermark.Watermark;

import javax.annotation.Nullable;

public class PulsarTopicStateWithPunctuatedWatermarks<T> extends PulsarTopicState {

    /** The timestamp assigner and watermark generator for the partition. */
    private final AssignerWithPunctuatedWatermarks<T> timestampsAndWatermarks;

    /** The last watermark timestamp generated by this partition. */
    private volatile long partitionWatermark;

    public PulsarTopicStateWithPunctuatedWatermarks(
            String topic,
            AssignerWithPunctuatedWatermarks<T> timestampsAndWatermarks) {
        super(topic);
        this.timestampsAndWatermarks = timestampsAndWatermarks;
        this.partitionWatermark = Long.MIN_VALUE;
    }

    public long getTimestampForRecord(T record, long timestamp) {
        return timestampsAndWatermarks.extractTimestamp(record, timestamp);
    }

    @Nullable
    public Watermark checkAndGetNewWatermark(T record, long timestamp) {
        Watermark mark = timestampsAndWatermarks.checkAndGetNextWatermark(record, timestamp);
        if (mark != null && mark.getTimestamp() > partitionWatermark) {
            partitionWatermark = mark.getTimestamp();
            return mark;
        }
        else {
            return null;
        }
    }

    public long getCurrentPartitionWatermark() {
        return partitionWatermark;
    }

    @Override
    public String toString() {
        return String.format("%s: %s, offset = %s, watermark = %d",
            getClass().getName(),
            getTopic(),
            getOffset(),
            partitionWatermark);
    }
}
