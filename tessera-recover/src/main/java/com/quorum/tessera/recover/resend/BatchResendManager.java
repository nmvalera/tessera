package com.quorum.tessera.recover.resend;

import com.quorum.tessera.ServiceLoaderUtil;
import com.quorum.tessera.config.Config;
import com.quorum.tessera.data.EncryptedTransactionDAO;
import com.quorum.tessera.data.EntityManagerDAOFactory;
import com.quorum.tessera.data.staging.StagingEntityDAO;
import com.quorum.tessera.enclave.Enclave;
import com.quorum.tessera.enclave.EnclaveFactory;
import com.quorum.tessera.partyinfo.*;

import java.util.Optional;

public interface BatchResendManager {

    ResendBatchResponse resendBatch(ResendBatchRequest request);

    void storeResendBatch(PushBatchRequest resendPushBatchRequest);

    static BatchResendManager create(Config config) {
        Optional<BatchResendManager> batchResendManagerOptional = ServiceLoaderUtil.load(BatchResendManager.class);

        if (batchResendManagerOptional.isPresent()) {
            return batchResendManagerOptional.get();
        }

        PartyInfoService partyInfoService = PartyInfoServiceFactory.create(config).partyInfoService();
        Enclave enclave = EnclaveFactory.create().create(config);
        EntityManagerDAOFactory entityManagerDAOFactory = EntityManagerDAOFactory.newFactory(config);

        EncryptedTransactionDAO encryptedTransactionDAO = entityManagerDAOFactory.createEncryptedTransactionDAO();
        StagingEntityDAO stagingEntityDAO = entityManagerDAOFactory.createStagingEntityDAO();

        return new BatchResendManagerImpl(
                enclave, stagingEntityDAO, encryptedTransactionDAO, partyInfoService);
    }
}