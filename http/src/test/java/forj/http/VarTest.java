package forj.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VarTest {

    @Test
    void longVarAcceptsExactlyTheLongs() {
        assertEquals(0L, Var.LongVar.parse("0"));
        assertEquals(-42L, Var.LongVar.parse("-42"));
        assertEquals(Long.MAX_VALUE, Var.LongVar.parse("9223372036854775807"));
        assertEquals(Long.MIN_VALUE, Var.LongVar.parse("-9223372036854775808"));
        assertNull(Var.LongVar.parse("9223372036854775808"));
        assertNull(Var.LongVar.parse("-9223372036854775809"));
        assertNull(Var.LongVar.parse("99999999999999999999"));
        for (String bad : new String[] {"", "-", "+1", " 1", "1 ", "1.0", "0x1", "١"}) {
            assertNull(Var.LongVar.parse(bad), bad);
        }
    }

    @Test
    void intVarAcceptsExactlyTheInts() {
        assertEquals(Integer.MAX_VALUE, Var.IntVar.parse("2147483647"));
        assertEquals(Integer.MIN_VALUE, Var.IntVar.parse("-2147483648"));
        assertNull(Var.IntVar.parse("2147483648"));
        assertNull(Var.IntVar.parse("abc"));
    }

    @Test
    void uuidVarAcceptsOnlyTheCanonicalForm() {
        UUID id = UUID.randomUUID();
        assertEquals(id, Var.UuidVar.parse(id.toString()));
        assertEquals(id, Var.UuidVar.parse(id.toString().toUpperCase()));
        assertNull(Var.UuidVar.parse("1-2-3-4-5"), "UUID.fromString would accept this");
        assertNull(Var.UuidVar.parse(id.toString().replace('-', '_')));
    }

    @Test
    void stringVarAcceptsAnyNonEmptySegment() {
        assertEquals("a b", Var.StringVar.parse("a b"));
        assertNull(Var.StringVar.parse(""));
    }

    @Test
    void customVars() {
        Var<Integer> even = Var.of("even", s -> Optional.ofNullable(Var.IntVar.parse(s)).filter(n -> n % 2 == 0));
        assertEquals(4, even.parse("4"));
        assertNull(even.parse("3"));
        assertEquals("{even}", even.toString());
    }

    @Test
    void pathsShowTheirSegments() {
        Path2<Long, String> p = Path.of("/api/users").then(Var.LongVar).then("files").then(Var.StringVar);
        assertEquals("/api/users/{long}/files/{string}", p.toString());
        assertEquals("/", Path.root().toString());
        assertThrows(IllegalArgumentException.class, () -> Path.root().then("a/b"));
        assertThrows(IllegalArgumentException.class, () -> Path.root().then(""));
    }
}
