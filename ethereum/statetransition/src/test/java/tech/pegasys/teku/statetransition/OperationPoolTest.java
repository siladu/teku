/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

@SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
public class OperationPoolTest {

  Spec spec = TestSpecFactory.createMinimalPhase0();
  DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  BeaconState state = mock(BeaconState.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  void init() {
    when(state.getSlot()).thenReturn(UInt64.ZERO);
  }

  @Test
  void emptyPoolShouldReturnEmptyList() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);

    OperationPool<ProposerSlashing> pool =
        new OperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldAddMaxItemsToPool() {
    OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new OperationPool<>(
            "SignedVoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);
    when(validator.validateFully(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForStateTransition(any(), any())).thenReturn(Optional.empty());
    final int maxVoluntaryExits = spec.getGenesisSpecConfig().getMaxVoluntaryExits();
    for (int i = 0; i < maxVoluntaryExits + 1; i++) {
      pool.addLocal(dataStructureUtil.randomSignedVoluntaryExit());
    }
    assertThat(pool.getItemsForBlock(state)).hasSize(maxVoluntaryExits);
  }

  @Test
  void shouldNotCountFilteredOperationsInMaxItems() {
    final Predicate<SignedVoluntaryExit> filter = mock(Predicate.class);
    OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new OperationPool<>(
            "SignedVoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);
    when(filter.test(any())).thenReturn(false);
    when(validator.validateFully(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForStateTransition(any(), any())).thenReturn(Optional.empty());
    final int maxVoluntaryExits = spec.getGenesisSpecConfig().getMaxVoluntaryExits();
    for (int i = 0; i < maxVoluntaryExits + 10; i++) {
      pool.addLocal(dataStructureUtil.randomSignedVoluntaryExit());
    }
    // Didn't find any applicable items but tried them all
    assertThat(pool.getItemsForBlock(state, filter, operation -> {})).isEmpty();
    verify(filter, times(maxVoluntaryExits + 10)).test(any());
  }

  @Test
  void shouldRemoveAllItemsFromPool() {
    OperationValidator<AttesterSlashing> validator = mock(OperationValidator.class);
    SszListSchema<AttesterSlashing, ?> attesterSlashingsSchema =
        beaconBlockSchemaSupplier
            .andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema)
            .apply(state.getSlot());
    OperationPool<AttesterSlashing> pool =
        new OperationPool<>(
            "AttesterSlashingPool", metricsSystem, __ -> attesterSlashingsSchema, validator);
    when(validator.validateFully(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForStateTransition(any(), any())).thenReturn(Optional.empty());
    SszList<AttesterSlashing> attesterSlashings =
        Stream.generate(() -> dataStructureUtil.randomAttesterSlashing())
            .limit(attesterSlashingsSchema.getMaxLength())
            .collect(attesterSlashingsSchema.collector());
    pool.removeAll(attesterSlashings);
    assertThat(pool.getItemsForBlock(state)).isEmpty();
  }

  @Test
  void shouldNotIncludeInvalidatedItemsFromPool() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool =
        new OperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateFully(any())).thenReturn(completedFuture(ACCEPT));

    pool.addLocal(slashing1);
    pool.addLocal(slashing2);

    when(validator.validateForStateTransition(any(), eq(slashing1)))
        .thenReturn(Optional.of(ExitInvalidReason.submittedTooEarly()));
    when(validator.validateForStateTransition(any(), eq(slashing2))).thenReturn(Optional.empty());

    assertThat(pool.getItemsForBlock(state)).containsOnly(slashing2);
  }

  @Test
  void subscribeOperationAdded() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool =
        new OperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);

    // Set up subscriber
    final Map<ProposerSlashing, InternalValidationResult> addedSlashings = new HashMap<>();
    OperationAddedSubscriber<ProposerSlashing> subscriber =
        (key, value, fromNetwork) -> addedSlashings.put(key, value);
    pool.subscribeOperationAdded(subscriber);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing3 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing4 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateFully(slashing1)).thenReturn(completedFuture(ACCEPT));
    when(validator.validateFully(slashing2)).thenReturn(completedFuture(SAVE_FOR_FUTURE));
    when(validator.validateFully(slashing3))
        .thenReturn(completedFuture(InternalValidationResult.reject("Nah")));
    when(validator.validateFully(slashing4)).thenReturn(completedFuture(IGNORE));

    pool.addLocal(slashing1);
    pool.addLocal(slashing2);
    pool.addLocal(slashing3);
    pool.addLocal(slashing4);

    assertThat(addedSlashings.size()).isEqualTo(2);
    assertThat(addedSlashings).containsKey(slashing1);
    assertThat(addedSlashings.get(slashing1).isAccept()).isTrue();
    assertThat(addedSlashings).containsKey(slashing2);
    assertThat(addedSlashings.get(slashing2).isSaveForFuture()).isTrue();
  }
}
