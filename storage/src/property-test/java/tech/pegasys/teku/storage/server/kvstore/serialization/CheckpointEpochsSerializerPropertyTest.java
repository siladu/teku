/*
 * Copyright 2022 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.CHECKPOINT_EPOCHS_SERIALIZER;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.CheckpointEpochs;

public class CheckpointEpochsSerializerPropertyTest {
  @Property
  void roundTrip(@ForAll final long justifiedEpoch, @ForAll final long finalizedEpoch) {
    final CheckpointEpochs value =
        new CheckpointEpochs(
            UInt64.fromLongBits(justifiedEpoch), UInt64.fromLongBits(finalizedEpoch));
    final byte[] serialized = CHECKPOINT_EPOCHS_SERIALIZER.serialize(value);
    final CheckpointEpochs deserialized = CHECKPOINT_EPOCHS_SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }
}
