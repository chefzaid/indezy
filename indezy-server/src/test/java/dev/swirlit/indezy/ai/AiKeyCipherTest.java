package dev.swirlit.indezy.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiKeyCipherTest {

    @Test
    void keepsAKeyUnreadableWithoutTheServersSecret() {
        AiKeyCipher cipher = new AiKeyCipher("server-secret");
        String sealed = cipher.encrypt("sk-ant-abc123");
        assertThat(sealed).doesNotContain("sk-ant").isNotEqualTo(cipher.encrypt("sk-ant-abc123"));
        assertThat(cipher.decrypt(sealed)).isEqualTo("sk-ant-abc123");
        assertThatThrownBy(() -> new AiKeyCipher("another-secret").decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }
}
