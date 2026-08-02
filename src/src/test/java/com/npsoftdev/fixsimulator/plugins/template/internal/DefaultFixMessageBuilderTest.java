package com.npsoftdev.fixsimulator.plugins.template.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.field.MsgType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import com.npsoftdev.fixsimulator.plugins.template.api.FieldSpec;
import com.npsoftdev.fixsimulator.plugins.template.api.FixMessageTemplate;
import com.npsoftdev.fixsimulator.plugins.template.api.PlaceholderType;

class DefaultFixMessageBuilderTest {

    private DefaultPlaceholderResolver placeholderResolver;
    private InMemoryValueMappingService mappingService;
    private DefaultFixMessageBuilder builder;

    private static final SessionID SESSION = new SessionID("FIX.4.4", "SIMULATOR", "EXCHANGE");

    @BeforeEach
    void setUp() {
        placeholderResolver = new DefaultPlaceholderResolver();
        mappingService      = new InMemoryValueMappingService();
        builder             = new DefaultFixMessageBuilder(placeholderResolver, mappingService, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private FixMessageTemplate newOrderSingleTemplate(List<FieldSpec> specs) {
        return FixMessageTemplate.builder()
                .id("test").name("Test").msgType("D")
                .fields(specs)
                .build();
    }

    // ── Literal fields ────────────────────────────────────────────────────────

    @Test
    void build_literalField_writesValueToMessage() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(55, "AAPL")  // Symbol
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("AAPL", msg.getString(55));
    }

    @Test
    void build_multipleLiteralFields_allWritten() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(55, "IBM"),
                FieldSpec.literal(38, "100"),  // OrderQty
                FieldSpec.literal(44, "150.50") // Price
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("IBM",    msg.getString(55));
        assertEquals("100",    msg.getString(38));
        assertEquals("150.50", msg.getString(44));
    }

    @Test
    void build_setsMessageTypeInHeader() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(55, "AAPL")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("D", msg.getHeader().getString(MsgType.FIELD));
    }

    // ── UserInput fields ──────────────────────────────────────────────────────

    @Test
    void build_userInput_usesOverrideValue() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.userInput(55, "symbol")
        ));

        Message msg = builder.build(tmpl, Map.of("symbol", "GOOG"), SESSION);

        assertEquals("GOOG", msg.getString(55));
    }

    @Test
    void build_userInput_usesDefaultWhenOverrideMissing() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.userInput(55, "symbol", "DEFAULT")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("DEFAULT", msg.getString(55));
    }

    @Test
    void build_userInput_blankOverrideFallsBackToDefault() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.userInput(55, "symbol", "FALLBACK")
        ));

        Message msg = builder.build(tmpl, Map.of("symbol", ""), SESSION);

        assertEquals("FALLBACK", msg.getString(55));
    }

    @Test
    void build_userInput_noDefaultAndNoOverride_fieldAbsent() {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.userInput(55, "symbol")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertFalse(msg.isSetField(55), "Tag 55 should not be set when no override and no default");
    }

    // ── Enumeration fields ────────────────────────────────────────────────────

    @Test
    void build_enumeration_usesOverrideValue() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.enumeration(54, "side", List.of("1", "2"), "1")
        ));

        Message msg = builder.build(tmpl, Map.of("side", "2"), SESSION);

        assertEquals("2", msg.getString(54));
    }

    @Test
    void build_enumeration_usesDefaultOptionWhenNoOverride() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.enumeration(54, "side", List.of("1", "2"), "1")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("1", msg.getString(54));
    }

    // ── Placeholder fields ────────────────────────────────────────────────────

    @Test
    void build_orderIdPlaceholder_writesNumericValue() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.placeholder(11, PlaceholderType.ORDER_ID)
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        String orderId = msg.getString(11);
        assertNotNull(orderId);
        assertDoesNotThrow(() -> Long.parseLong(orderId));
    }

    @Test
    void build_sessionSenderPlaceholder_writesSenderCompId() throws Exception {
        // Use tag 115 (OnBehalfOfCompID) — non-engine-owned — to demonstrate SESSION_SENDER
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.placeholder(115, PlaceholderType.SESSION_SENDER)
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("SIMULATOR", msg.getString(115));
    }

    @Test
    void build_sessionTargetPlaceholder_writesTargetCompId() throws Exception {
        // Use tag 128 (DeliverToCompID) — non-engine-owned — to demonstrate SESSION_TARGET
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.placeholder(128, PlaceholderType.SESSION_TARGET)
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("EXCHANGE", msg.getString(128));
    }

    // ── Derived fields ────────────────────────────────────────────────────────

    @Test
    void build_derivedField_lookupsValueFromMappingService() throws Exception {
        // InMemoryValueMappingService pre-seeds "symbol-to-isin" with AAPL → US0378331005
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(55, "AAPL"),
                FieldSpec.derived(48, 55, "symbol-to-isin")  // SecurityID derived from Symbol
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("AAPL",           msg.getString(55));
        assertEquals("US0378331005",   msg.getString(48));
    }

    @Test
    void build_derivedField_skippedWhenSourceTagMissing() {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                // No tag 55 in this template — derived tag 48 should be silently skipped
                FieldSpec.derived(48, 55, "symbol-to-isin")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertFalse(msg.isSetField(48), "Derived tag should be absent when source is missing");
    }

    @Test
    void build_derivedField_skippedWhenMappingHasNoMatch() {
        // "UNKNOWN_TICKER" has no entry in the pre-seeded mapping
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(55, "UNKNOWN_TICKER"),
                FieldSpec.derived(48, 55, "symbol-to-isin")
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertFalse(msg.isSetField(48), "Derived tag should be absent when mapping has no match");
    }

    // ── Engine-owned field skipping ───────────────────────────────────────────

    @Test
    void build_engineOwnedTags_skippedDuringWrite() {
        // Tags 8 (BeginString), 9 (BodyLength), 10 (Checksum), 34 (MsgSeqNum)
        // 35 (MsgType), 49 (SenderCompID), 52 (SendingTime), 56 (TargetCompID)
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(8,  "FIX.4.2"),  // BeginString — engine-owned
                FieldSpec.literal(49, "FAKE"),       // SenderCompID — engine-owned
                FieldSpec.literal(55, "AAPL")        // Symbol — should be written
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        // Tag 55 must be set; engine-owned tags 8 and 49 must NOT be set by the builder
        try { assertEquals("AAPL", msg.getString(55)); }
        catch (quickfix.FieldNotFound e) { fail("Tag 55 should be set"); }
        // Tag 49 (SenderCompID) is engine-owned and should NOT be written in the body
        assertFalse(msg.isSetField(49), "Engine-owned SenderCompID must not be written to body");
    }

    // ── Dynamic token resolution in Literal ───────────────────────────────────

    @Test
    void build_literalWithOrderIdToken_isSubstituted() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(11, "ORD-$(order_id)")  // ClOrdID with embedded token
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        String clOrdId = msg.getString(11);
        assertTrue(clOrdId.startsWith("ORD-"), "Expected prefix 'ORD-' in: " + clOrdId);
        // The suffix should be a number
        String suffix = clOrdId.substring(4);
        assertDoesNotThrow(() -> Long.parseLong(suffix), "Suffix should be numeric: " + suffix);
    }

    @Test
    void build_literalWithSenderToken_isSubstituted() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(57, "$(sender)-DESK")  // TradeDesk with sender token
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("SIMULATOR-DESK", msg.getString(57));
    }

    @Test
    void build_literalWithUnknownToken_isLeftAsIs() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.literal(58, "REF-$(unknown_token)")  // Text field
        ));

        Message msg = builder.build(tmpl, Map.of(), SESSION);

        assertEquals("REF-$(unknown_token)", msg.getString(58));
    }

    // ── Null overrides map ────────────────────────────────────────────────────

    @Test
    void build_nullOverrides_treatedAsEmpty() throws Exception {
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.userInput(55, "symbol", "DFLT")
        ));

        Message msg = builder.build(tmpl, null, SESSION);

        assertEquals("DFLT", msg.getString(55));
    }

    // ── Two-pass ordering: Derived can reference UserInput resolved in Pass 1 ──

    @Test
    void build_derivedReferencesUserInputField_resolvedCorrectly() throws Exception {
        // InMemoryValueMappingService pre-seeds TSLA → US88160R1014
        FixMessageTemplate tmpl = newOrderSingleTemplate(List.of(
                FieldSpec.derived(48, 55, "symbol-to-isin"),  // declared BEFORE tag 55
                FieldSpec.userInput(55, "symbol")              // resolved in Pass 1
        ));

        Message msg = builder.build(tmpl, Map.of("symbol", "TSLA"), SESSION);

        assertEquals("TSLA",           msg.getString(55));
        assertEquals("US88160R1014",   msg.getString(48));
    }

}
