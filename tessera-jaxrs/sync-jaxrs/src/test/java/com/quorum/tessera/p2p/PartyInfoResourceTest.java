package com.quorum.tessera.p2p;

import com.quorum.tessera.enclave.Enclave;
import com.quorum.tessera.enclave.EncodedPayload;
import com.quorum.tessera.enclave.PayloadEncoder;
import com.quorum.tessera.encryption.PublicKey;
import com.quorum.tessera.partyinfo.PartyInfoParser;
import com.quorum.tessera.partyinfo.PartyInfoService;
import com.quorum.tessera.partyinfo.model.Party;
import com.quorum.tessera.partyinfo.model.PartyInfo;
import com.quorum.tessera.partyinfo.model.Recipient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.json.Json;
import javax.json.JsonReader;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.*;

public class PartyInfoResourceTest {

    private PartyInfoService partyInfoService;

    private PartyInfoResource partyInfoResource;

    private PartyInfoParser partyInfoParser;

    private Enclave enclave;

    private Client restClient;

    private PayloadEncoder payloadEncoder;

    @Before
    public void onSetup() {
        this.partyInfoService = mock(PartyInfoService.class);
        this.partyInfoParser = mock(PartyInfoParser.class);
        this.enclave = mock(Enclave.class);
        this.restClient = mock(Client.class);
        this.payloadEncoder = mock(PayloadEncoder.class);
        this.partyInfoResource =
                new PartyInfoResource(partyInfoService, partyInfoParser, restClient, enclave, payloadEncoder, true);
    }

    @After
    public void onTearDown() {
        verifyNoMoreInteractions(partyInfoService, partyInfoParser, restClient, enclave, payloadEncoder);
    }

    @Test
    public void partyInfoGet() {

        final String partyInfoJson =
                "{\"url\":\"http://localhost:9001/\",\"peers\":[{\"url\":\"http://localhost:9006/\",\"lastContact\":null},{\"url\":\"http://localhost:9005/\",\"lastContact\":\"2019-01-02T15:03:22.875Z\"}],\"keys\":[{\"key\":\"BULeR8JyUWhiuuCMU/HLA0Q5pzkYT+cHII3ZKBey3Bo=\",\"url\":\"http://localhost:9001/\"},{\"key\":\"QfeDAys9MPDs2XHExtc84jKGHxZg/aj52DTh0vtA3Xc=\",\"url\":\"http://localhost:9002/\"}]}";

        final Party partyWithoutTimestamp = new Party("http://localhost:9006/");
        final Party partyWithTimestamp = new Party("http://localhost:9005/");
        partyWithTimestamp.setLastContacted(Instant.parse("2019-01-02T15:03:22.875Z"));

        final PartyInfo partyInfo =
                new PartyInfo(
                        "http://localhost:9001/",
                        new HashSet<>(
                                Arrays.asList(
                                        Recipient.of(
                                                PublicKey.from(
                                                        Base64.getDecoder()
                                                                .decode(
                                                                        "BULeR8JyUWhiuuCMU/HLA0Q5pzkYT+cHII3ZKBey3Bo=")),
                                                "http://localhost:9001/"),
                                        Recipient.of(
                                                PublicKey.from(
                                                        Base64.getDecoder()
                                                                .decode(
                                                                        "QfeDAys9MPDs2XHExtc84jKGHxZg/aj52DTh0vtA3Xc=")),
                                                "http://localhost:9002/"))),
                        new HashSet<>(Arrays.asList(partyWithTimestamp, partyWithoutTimestamp)));

        when(partyInfoService.getPartyInfo()).thenReturn(partyInfo);

        final Response response = partyInfoResource.getPartyInfo();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);

        final String output = response.getEntity().toString();
        final JsonReader expected = Json.createReader(new StringReader(partyInfoJson));
        final JsonReader actual = Json.createReader(new StringReader(output));

        assertThat(expected.readObject()).isEqualTo(actual.readObject());

        verify(partyInfoService).getPartyInfo();
    }

    @Test
    public void partyInfo() {

        String url = "http://www.bogus.com";

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        PublicKey recipientKey = PublicKey.from("recipientKey".getBytes());

        String message = "I love sparrows";

        byte[] payload = message.getBytes();

        Recipient recipient = Recipient.of(recipientKey, url);

        Set<Recipient> recipientList = Collections.singleton(recipient);

        PartyInfo partyInfo = new PartyInfo(url, recipientList, Collections.emptySet());

        when(partyInfoParser.from(payload)).thenReturn(partyInfo);

        when(enclave.defaultPublicKey()).thenReturn(myKey);

        when(partyInfoParser.to(partyInfo)).thenReturn(payload);

        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        List<String> uuidList = new ArrayList<>();
        doAnswer(
                        (invocation) -> {
                            byte[] d = invocation.getArgument(0);
                            uuidList.add(new String(d));
                            return encodedPayload;
                        })
                .when(enclave)
                .encryptPayload(any(byte[].class), any(PublicKey.class), anyList());

        when(payloadEncoder.encode(encodedPayload)).thenReturn(payload);

        WebTarget webTarget = mock(WebTarget.class);
        when(restClient.target(url)).thenReturn(webTarget);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        Invocation.Builder invocationBuilder = mock(Invocation.Builder.class);
        when(webTarget.request()).thenReturn(invocationBuilder);

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);

        doAnswer((invocation) -> uuidList.get(0)).when(response).readEntity(String.class);

        when(invocationBuilder.post(any(Entity.class))).thenReturn(response);

        when(partyInfoService.updatePartyInfo(any(PartyInfo.class))).thenReturn(partyInfo);

        Response result = partyInfoResource.partyInfo(payload);

        assertThat(result.getStatus()).isEqualTo(200);

        verify(partyInfoParser).from(payload);
        verify(enclave).defaultPublicKey();
        verify(enclave).encryptPayload(any(byte[].class), any(PublicKey.class), anyList());
        verify(payloadEncoder).encode(encodedPayload);
        verify(restClient).target(url);
        verify(partyInfoService).updatePartyInfo(any(PartyInfo.class));
    }

    @Test
    public void validate() {

        String message = UUID.randomUUID().toString();

        byte[] payload = message.getBytes();

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        EncodedPayload encodedPayload = mock(EncodedPayload.class);
        when(encodedPayload.getRecipientKeys()).thenReturn(Collections.singletonList(myKey));

        when(payloadEncoder.decode(payload)).thenReturn(encodedPayload);

        when(enclave.unencryptTransaction(encodedPayload, myKey)).thenReturn(message.getBytes());

        Response result = partyInfoResource.validate(payload);

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getEntity()).isEqualTo(message);

        verify(payloadEncoder).decode(payload);
        verify(enclave).unencryptTransaction(encodedPayload, myKey);
    }

    @Test
    public void validateReturns400IfMessageIsNotUUID() {

        String message = "I love sparrows";

        byte[] payload = message.getBytes();

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        EncodedPayload encodedPayload = mock(EncodedPayload.class);
        when(encodedPayload.getRecipientKeys()).thenReturn(Collections.singletonList(myKey));

        when(payloadEncoder.decode(payload)).thenReturn(encodedPayload);

        when(enclave.unencryptTransaction(encodedPayload, myKey)).thenReturn(message.getBytes());

        Response result = partyInfoResource.validate(payload);

        assertThat(result.getStatus()).isEqualTo(400);
        assertThat(result.getEntity()).isNull();

        verify(payloadEncoder).decode(payload);
        verify(enclave).unencryptTransaction(encodedPayload, myKey);
    }

    @Test
    public void constructWithMinimalArgs() {
        PartyInfoResource instance =
                new PartyInfoResource(partyInfoService, partyInfoParser, restClient, enclave, true);
        assertThat(instance).isNotNull();
    }

    @Test
    public void partyInfoExceptionIfValidationFailsWith200() {

        final int validateResponseCode = 200;
        final String validateResponseMsg = "BADRESPONSE";

        String url = "http://www.bogus.com";

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        PublicKey recipientKey = PublicKey.from("recipientKey".getBytes());

        String message = "I love sparrows";

        byte[] payload = message.getBytes();

        Recipient recipient = Recipient.of(recipientKey, url);

        Set<Recipient> recipientList = Collections.singleton(recipient);

        PartyInfo partyInfo = new PartyInfo(url, recipientList, Collections.emptySet());

        when(partyInfoParser.from(payload)).thenReturn(partyInfo);

        when(enclave.defaultPublicKey()).thenReturn(myKey);

        when(partyInfoParser.to(partyInfo)).thenReturn(payload);

        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        when(enclave.encryptPayload(any(byte[].class), any(PublicKey.class), anyList())).thenReturn(encodedPayload);

        when(payloadEncoder.encode(encodedPayload)).thenReturn(payload);

        WebTarget webTarget = mock(WebTarget.class);
        when(restClient.target(url)).thenReturn(webTarget);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        Invocation.Builder invocationBuilder = mock(Invocation.Builder.class);
        when(webTarget.request()).thenReturn(invocationBuilder);

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(validateResponseCode);

        doAnswer((invocation) -> validateResponseMsg).when(response).readEntity(String.class);

        when(invocationBuilder.post(any(Entity.class))).thenReturn(response);

        try {
            partyInfoResource.partyInfo(payload);
            failBecauseExceptionWasNotThrown(SecurityException.class);
        } catch (SecurityException ex) {
            verify(partyInfoParser).from(payload);
            verify(enclave).defaultPublicKey();
            verify(enclave).encryptPayload(any(byte[].class), any(PublicKey.class), anyList());
            verify(payloadEncoder).encode(encodedPayload);
            verify(restClient).target(url);
        }
    }

    @Test
    public void partyInfoExceptionIfValidationFailsWith400() {

        final int validateResponseCode = 400;
        final String validateResponseMsg = null;

        String url = "http://www.bogus.com";

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        PublicKey recipientKey = PublicKey.from("recipientKey".getBytes());

        String message = "I love sparrows";

        byte[] payload = message.getBytes();

        Recipient recipient = Recipient.of(recipientKey, url);

        Set<Recipient> recipientList = Collections.singleton(recipient);

        PartyInfo partyInfo = new PartyInfo(url, recipientList, Collections.emptySet());

        when(partyInfoParser.from(payload)).thenReturn(partyInfo);

        when(enclave.defaultPublicKey()).thenReturn(myKey);

        when(partyInfoParser.to(partyInfo)).thenReturn(payload);

        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        when(enclave.encryptPayload(any(byte[].class), any(PublicKey.class), anyList())).thenReturn(encodedPayload);

        when(payloadEncoder.encode(encodedPayload)).thenReturn(payload);

        WebTarget webTarget = mock(WebTarget.class);
        when(restClient.target(url)).thenReturn(webTarget);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        Invocation.Builder invocationBuilder = mock(Invocation.Builder.class);
        when(webTarget.request()).thenReturn(invocationBuilder);

        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(validateResponseCode);

        doAnswer((invocation) -> validateResponseMsg).when(response).readEntity(String.class);

        when(invocationBuilder.post(any(Entity.class))).thenReturn(response);

        try {
            partyInfoResource.partyInfo(payload);
            failBecauseExceptionWasNotThrown(SecurityException.class);
        } catch (SecurityException ex) {
            verify(partyInfoParser).from(payload);
            verify(enclave).defaultPublicKey();
            verify(enclave).encryptPayload(any(byte[].class), any(PublicKey.class), anyList());
            verify(payloadEncoder).encode(encodedPayload);
            verify(restClient).target(url);
        }
    }

    @Test
    public void partyInfoValidateThrowsException() {

        String url = "http://www.bogus.com";

        PublicKey myKey = PublicKey.from("myKey".getBytes());

        PublicKey recipientKey = PublicKey.from("recipientKey".getBytes());

        String message = "I love sparrows";

        byte[] payload = message.getBytes();

        Recipient recipient = Recipient.of(recipientKey, url);

        Set<Recipient> recipientList = Collections.singleton(recipient);

        PartyInfo partyInfo = new PartyInfo(url, recipientList, Collections.emptySet());

        when(partyInfoParser.from(payload)).thenReturn(partyInfo);

        when(enclave.defaultPublicKey()).thenReturn(myKey);

        when(partyInfoParser.to(partyInfo)).thenReturn(payload);

        EncodedPayload encodedPayload = mock(EncodedPayload.class);

        when(enclave.encryptPayload(any(byte[].class), any(PublicKey.class), anyList())).thenReturn(encodedPayload);

        when(payloadEncoder.encode(encodedPayload)).thenReturn(payload);

        WebTarget webTarget = mock(WebTarget.class);
        when(restClient.target(url)).thenReturn(webTarget);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        Invocation.Builder invocationBuilder = mock(Invocation.Builder.class);
        when(webTarget.request()).thenReturn(invocationBuilder);

        when(invocationBuilder.post(any(Entity.class)))
                .thenThrow(new UncheckedIOException(new IOException("GURU meditation")));

        try {
            partyInfoResource.partyInfo(payload);
            failBecauseExceptionWasNotThrown(SecurityException.class);
        } catch (SecurityException ex) {
            verify(partyInfoParser).from(payload);
            verify(enclave).defaultPublicKey();
            verify(enclave).encryptPayload(any(byte[].class), any(PublicKey.class), anyList());
            verify(payloadEncoder).encode(encodedPayload);
            verify(restClient).target(url);
        }
    }

    @Test
    public void validationDisabledPassesAllKeysToStore() {
        this.partyInfoResource =
                new PartyInfoResource(partyInfoService, partyInfoParser, restClient, enclave, payloadEncoder, false);

        final byte[] payload = "Test message".getBytes();

        final String url = "http://www.bogus.com";
        final String otherurl = "http://www.randomaddress.com";
        final PublicKey recipientKey = PublicKey.from("recipientKey".getBytes());
        final Set<Recipient> recipientList =
                new HashSet<>(Arrays.asList(Recipient.of(recipientKey, url), Recipient.of(recipientKey, otherurl)));
        final PartyInfo partyInfo = new PartyInfo(url, recipientList, Collections.emptySet());

        final ArgumentCaptor<PartyInfo> captor = ArgumentCaptor.forClass(PartyInfo.class);
        final byte[] serialisedData = "SERIALISED".getBytes();

        when(partyInfoParser.from(payload)).thenReturn(partyInfo);
        when(partyInfoService.getPartyInfo()).thenReturn(partyInfo);
        when(partyInfoParser.to(captor.capture())).thenReturn(serialisedData);

        final Response callResponse = partyInfoResource.partyInfo(payload);
        final byte[] data = (byte[]) callResponse.getEntity();

        assertThat(captor.getValue().getUrl()).isEqualTo(url);
        assertThat(captor.getValue().getRecipients()).isEmpty();
        assertThat(captor.getValue().getParties()).isEmpty();
        assertThat(new String(data)).isEqualTo("SERIALISED");
        verify(partyInfoParser).from(payload);
        verify(partyInfoParser).to(any(PartyInfo.class));

        final ArgumentCaptor<PartyInfo> modifiedPartyInfoCaptor = ArgumentCaptor.forClass(PartyInfo.class);

        verify(partyInfoService).updatePartyInfo(modifiedPartyInfoCaptor.capture());
        final PartyInfo modified = modifiedPartyInfoCaptor.getValue();

        assertThat(modified.getUrl()).isEqualTo(url);

        Set<Recipient> updatedRecipients = modified.getRecipients();
        assertThat(updatedRecipients)
                .containsExactlyInAnyOrder(Recipient.of(recipientKey, url), Recipient.of(recipientKey, otherurl));

        assertThat(modified.getParties()).isEmpty();

        verify(partyInfoService).getPartyInfo();
    }

    @Test
    public void partyInfoValidationEncryptsUniqueDataForEachKey() {
        String url = "http://bogus";
        Set<Party> parties = Collections.emptySet();
        Set<Recipient> recipients = new HashSet<>();
        recipients.add(Recipient.of(mock(PublicKey.class), url));
        recipients.add(Recipient.of(mock(PublicKey.class), url));

        PartyInfo partyInfo = new PartyInfo(url, recipients, parties);

        byte[] payload = new byte[]{};
        when(partyInfoParser.from(payload)).thenReturn(partyInfo);

        when(enclave.defaultPublicKey()).thenReturn(PublicKey.from("defaultKey".getBytes()));
        EncodedPayload encodedPayload = mock(EncodedPayload.class);
        List<String> uuidList = new ArrayList<>();
        doAnswer(
            (invocation) -> {
                byte[] d = invocation.getArgument(0);
                uuidList.add(new String(d));
                return encodedPayload;
            })
            .when(enclave)
            .encryptPayload(any(byte[].class), any(PublicKey.class), anyList());

        when(payloadEncoder.encode(any(EncodedPayload.class))).thenReturn("somedata".getBytes());

        WebTarget webTarget = mock(WebTarget.class);
        when(restClient.target(url)).thenReturn(webTarget);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        Invocation.Builder invocationBuilder = mock(Invocation.Builder.class);
        when(webTarget.request()).thenReturn(invocationBuilder);
        Response response = mock(Response.class);
        when(invocationBuilder.post(any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn("");

        doAnswer(new Answer() {
            private int i = 0;

            public Object answer(InvocationOnMock invocation) {
                String result = uuidList.get(i);
                i++;
                return result;
            }
        }).when(response).readEntity(String.class);

        when(partyInfoService.updatePartyInfo(any(PartyInfo.class))).thenReturn(partyInfo);

        // the test
        partyInfoResource.partyInfo(payload);

        ArgumentCaptor<byte[]> uuidCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(enclave, times(2)).encryptPayload(uuidCaptor.capture(), any(PublicKey.class), anyList());
        List<byte[]> capturedUUIDs = uuidCaptor.getAllValues();
        assertThat(capturedUUIDs).hasSize(2);
        assertThat(capturedUUIDs.get(0)).isNotEqualTo(capturedUUIDs.get(1));

        // other verifications
        verify(partyInfoService).updatePartyInfo(any(PartyInfo.class));
        verify(partyInfoParser).from(payload);
        verify(enclave).defaultPublicKey();
        verify(payloadEncoder, times(2)).encode(encodedPayload);
        verify(restClient, times(2)).target(url);
    }
}
