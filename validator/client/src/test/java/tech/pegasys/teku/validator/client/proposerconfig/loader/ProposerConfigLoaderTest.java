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

package tech.pegasys.teku.validator.client.proposerconfig.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.net.URL;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.eth1.Eth1Address;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;

public class ProposerConfigLoaderTest {
  private final ProposerConfigLoader loader = new ProposerConfigLoader();

  @Test
  void shouldLoadValidConfigFromUrl() {
    final URL resource = Resources.getResource("proposerConfigValid1.json");

    validateContent1(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithEmptyProposerConfig() {
    final URL resource = Resources.getResource("proposerConfigValid2.json");

    validateContent2(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithOnlyDefaultValidatorRegistrationEnabled() {
    final URL resource = Resources.getResource("proposerConfigWithRegistrationValid1.json");

    validateContentWithValidatorRegistration1(loader.getProposerConfig(resource));
  }

  @Test
  void shouldLoadConfigWithDefaultValidatorRegistrationDisabled() {
    final URL resource = Resources.getResource("proposerConfigWithRegistrationValid2.json");

    validateContentWithValidatorRegistration2(loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadInvalidPubKey() {
    final URL resource = Resources.getResource("proposerConfigInvalid1.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadNullFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigInvalid2.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadInvalidFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigInvalid2.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadMissingFeeRecipient() {
    final URL resource = Resources.getResource("proposerConfigInvalid4.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadMissingEnabledInRegistration() {
    final URL resource = Resources.getResource("proposerConfigWithRegistrationInvalid1.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  @Test
  void shouldNotLoadMissingDefault() {
    final URL resource = Resources.getResource("proposerConfigInvalid5.json");

    assertThatThrownBy(() -> loader.getProposerConfig(resource));
  }

  private void validateContent1(ProposerConfig config) {
    Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isPresent();
    assertThat(theConfig.get().getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x50155530FCE8a85ec7055A5F8b2bE214B3DaeFd3"));

    Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A"));
  }

  private void validateContent2(ProposerConfig config) {
    Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isEmpty();

    Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A"));
  }

  private void validateContentWithValidatorRegistration1(ProposerConfig config) {
    Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isEmpty();

    Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A"));

    assertThat(defaultConfig.getValidatorRegistration()).isPresent();
    assertThat(defaultConfig.getValidatorRegistration().get().isEnabled()).isTrue();
  }

  private void validateContentWithValidatorRegistration2(ProposerConfig config) {
    Optional<Config> theConfig =
        config.getConfigForPubKey(
            "0xa057816155ad77931185101128655c0191bd0214c201ca48ed887f6c4c6adf334070efcd75140eada5ac83a92506dd7a");
    assertThat(theConfig).isPresent();
    assertThat(theConfig.get().getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x50155530FCE8a85ec7055A5F8b2bE214B3DaeFd3"));

    assertThat(theConfig.get().getValidatorRegistration()).isPresent();
    ProposerConfig.ValidatorRegistration validatorRegistration =
        theConfig.get().getValidatorRegistration().get();
    assertThat(validatorRegistration.isEnabled()).isTrue();
    assertThat(validatorRegistration.getGasLimit().orElseThrow())
        .isEqualTo(UInt64.valueOf(12345654321L));

    Config defaultConfig = config.getDefaultConfig();
    assertThat(defaultConfig.getFeeRecipient())
        .isEqualTo(Eth1Address.fromHexString("0x6e35733c5af9B61374A128e6F85f553aF09ff89A"));

    assertThat(defaultConfig.getValidatorRegistration()).isPresent();
    assertThat(defaultConfig.getValidatorRegistration().get().isEnabled()).isFalse();
    assertThat(defaultConfig.getValidatorRegistration().get().getGasLimit()).isEmpty();
  }
}
