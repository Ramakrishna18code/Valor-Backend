package com.valor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:profile-photo;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "app.profile-photos.local-root=${java.io.tmpdir}/valor-profile-photo-tests"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ProfilePhotoTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepo users;
    @Autowired CustomerRepo customers; @Autowired TechRepo technicians; @Autowired JwtService jwt;
    String token(Role role) {
        User user = new User(); user.setRole(role); user.setEmail(UUID.randomUUID()+"@photo.test"); user.setPasswordHash("unused"); users.saveAndFlush(user);
        if (role == Role.CUSTOMER) { var profile = new CustomerProfile(); profile.user = user; profile.fullName = "Photo Customer"; customers.saveAndFlush(profile); }
        if (role == Role.TECHNICIAN) { var profile = new TechnicianProfile(); profile.user = user; profile.employeeId = UUID.randomUUID().toString(); technicians.saveAndFlush(profile); }
        return jwt.issue(user);
    }
    byte[] image(int width, int height) throws Exception {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB), "png", output); return output.toByteArray();
    }
    String read(String token) throws Exception {
        var response = mvc.perform(get("/api/v1/profile/photo").header("Authorization", "Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("data").get("dataUri").asText(null);
    }
    @Test void bothRolesCanSaveReplaceReadAndRemoveTheirOwnPhoto() throws Exception {
        for (Role role : List.of(Role.CUSTOMER,Role.TECHNICIAN)) {
            String owner = token(role), other = token(role);
            assertNull(read(owner));
            mvc.perform(multipart("/api/v1/profile/photo").file(new MockMultipartFile("file","photo.png","image/png",image(400,300))).header("Authorization","Bearer "+owner)).andExpect(status().isOk());
            String saved = read(owner); assertTrue(saved.startsWith("data:image/jpeg;base64,"));
            var normalized = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(saved.split(",")[1])));
            assertEquals(256,normalized.getWidth()); assertEquals(256,normalized.getHeight());
            assertNull(read(other));
            mvc.perform(multipart("/api/v1/profile/photo").file(new MockMultipartFile("file","replacement.png","image/png",image(120,120))).header("Authorization","Bearer "+owner)).andExpect(status().isOk());
            mvc.perform(delete("/api/v1/profile/photo").header("Authorization","Bearer "+other)).andExpect(status().isOk()); assertNotNull(read(owner));
            mvc.perform(delete("/api/v1/profile/photo").header("Authorization","Bearer "+owner)).andExpect(status().isOk()); assertNull(read(owner));
        }
    }
    @Test void invalidUploadsLeaveExistingPhotoIntact() throws Exception {
        String owner = token(Role.CUSTOMER);
        mvc.perform(multipart("/api/v1/profile/photo").file(new MockMultipartFile("file","valid.png","image/png",image(80,80))).header("Authorization","Bearer "+owner)).andExpect(status().isOk());
        String original = read(owner);
        for (byte[] bytes : List.of(new byte[0], "not an image".getBytes(), new byte[5*1024*1024+1])) {
            mvc.perform(multipart("/api/v1/profile/photo").file(new MockMultipartFile("file","fake.jpg","image/jpeg",bytes)).header("Authorization","Bearer "+owner)).andExpect(status().isBadRequest()); assertEquals(original,read(owner));
        }
        mvc.perform(delete("/api/v1/profile/photo").header("Authorization","Bearer "+owner)).andExpect(status().isOk());
    }
    @Test void requiresAuthenticatedCustomerOrTechnician() throws Exception {
        mvc.perform(get("/api/v1/profile/photo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile/photo").header("Authorization","Bearer "+token(Role.ADMIN))).andExpect(status().isForbidden());
    }
}
