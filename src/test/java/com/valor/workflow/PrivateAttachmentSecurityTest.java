package com.valor.workflow;

import com.valor.auth.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivateAttachmentSecurityTest {
    private final TechnicianPrivateAttachmentRepository repository = mock(TechnicianPrivateAttachmentRepository.class);
    private final AssetIdentityAccess identities = mock(AssetIdentityAccess.class);
    private final WorkflowIdentityAccess profiles = mock(WorkflowIdentityAccess.class);
    private final RequestAttachmentStorage storage = mock(RequestAttachmentStorage.class);
    private final TechnicianPrivateAttachmentService service = new TechnicianPrivateAttachmentService(repository, identities, profiles, storage);

    private void technician() {
        User actor = new User(); actor.setRole(Role.TECHNICIAN);
        TechnicianProfile profile = mock(TechnicianProfile.class);
        when(profile.getId()).thenReturn(1L);
        when(identities.actor()).thenReturn(actor);
        when(profiles.technician(actor)).thenReturn(profile);
    }

    static Stream<String> types() { return Stream.of("image/png", "image/jpeg", "image/webp", "application/pdf"); }

    @ParameterizedTest @MethodSource("types")
    void rejectsDisguisedContentBeforeStorage(String type) {
        technician();
        var file = new MockMultipartFile("file", "document", type, "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII));
        assertEquals(400, assertThrows(WorkflowException.class, () -> service.upload(1L, file)).status);
        verifyNoInteractions(storage, repository);
    }

    @Test void deniesAnotherTechniciansFilesBeforeStorage() {
        technician();
        var file = new MockMultipartFile("file", "file.pdf", "application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        assertThrows(AccessDeniedException.class, () -> service.upload(2L, file));
        assertThrows(AccessDeniedException.class, () -> service.download(2L, 1L));
        assertThrows(AccessDeniedException.class, () -> service.delete(2L, 1L));
        verifyNoInteractions(storage, repository);
    }

    @Test void acceptsSupportedHeaderAndStoresContent() throws Exception {
        technician();
        when(storage.store(eq(".pdf"), any())).thenReturn(new RequestAttachmentStorage.StoredFile("safe-key"));
        when(repository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var file = new MockMultipartFile("file", "file.pdf", "application/pdf", "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        assertEquals("application/pdf", service.upload(1L, file).contentType());
        verify(storage).store(eq(".pdf"), any());
    }

    @Test void rejectsPartialAndMismatchedSignatures() {
        assertFalse(AttachmentContent.matches("image/png", new byte[]{(byte)137,80,78,71,0,0,0,0}));
        assertFalse(AttachmentContent.matches("image/jpeg", new byte[]{(byte)255,(byte)216,0,0}));
        assertFalse(AttachmentContent.matches("application/pdf", "%PDF fake".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(AttachmentContent.matches("image/webp", "RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII)));
        for (String type : types().toList()) assertFalse(AttachmentContent.matches(type, new byte[0]));
        assertTrue(AttachmentContent.matches("image/png", new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertTrue(AttachmentContent.matches("image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}));
        assertTrue(AttachmentContent.matches("image/webp", "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)));
    }
}
