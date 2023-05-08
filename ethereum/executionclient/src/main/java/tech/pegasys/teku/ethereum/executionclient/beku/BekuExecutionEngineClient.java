package tech.pegasys.teku.ethereum.executionclient.beku;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

import java.util.Optional;

public class BekuExecutionEngineClient implements ExecutionEngineClient {

    //TODO need a reference to Besu engine api methods

    @Override
    public SafeFuture<PowBlock> getPowBlock(Bytes32 blockHash) {
        return null;
    }

    @Override
    public SafeFuture<PowBlock> getPowChainHead() {
        return null;
    }

    @Override
    public SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(Bytes8 payloadId) {
        return null;
    }

    @Override
    public SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(Bytes8 payloadId) {
        return null;
    }

    @Override
    public SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(Bytes8 payloadId) {
        return null;
    }

    @Override
    public SafeFuture<Response<PayloadStatusV1>> newPayloadV1(ExecutionPayloadV1 executionPayload) {
        return null;
    }

    @Override
    public SafeFuture<Response<PayloadStatusV1>> newPayloadV2(ExecutionPayloadV1 executionPayload) {
        return null;
    }

    @Override
    public SafeFuture<Response<PayloadStatusV1>> newPayloadV3(ExecutionPayloadV1 executionPayload) {
        return null;
    }

    @Override
    public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
        return null;
    }

    @Override
    public SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes) {
        return null;
    }

    @Override
    public SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(TransitionConfigurationV1 transitionConfiguration) {
        return null;
    }

    @Override
    public SafeFuture<Response<List<String>>> exchangeCapabilities(final List<String> capabilities) {
        return null;
    }
}
