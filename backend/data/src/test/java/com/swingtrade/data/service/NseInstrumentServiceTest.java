package com.swingtrade.data.service;

// Upstox integration is unconfigured — NseInstrumentService (Upstox instrument master)
// is commented out, so its tests are commented out too (kept, not deleted).
//
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.web.reactive.function.client.WebClient;
//
// import java.util.Optional;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// class NseInstrumentServiceTest {
//
//     private NseInstrumentService service;
//
//     @BeforeEach
//     void setUp() {
//         // Use a builder that points at nothing — we won't call loadInstruments() in unit tests
//         service = new NseInstrumentService(WebClient.builder());
//     }
//
//     @Test
//     void parsesNseEqEquityInstruments() {
//         String json = """
//             [
//               {"segment":"NSE_EQ","instrument_type":"EQ","trading_symbol":"RELIANCE",
//                "instrument_key":"NSE_EQ|INE002A01018","name":"RELIANCE INDUSTRIES","isin":"INE002A01018"},
//               {"segment":"NSE_EQ","instrument_type":"EQ","trading_symbol":"INFY",
//                "instrument_key":"NSE_EQ|INE009A01021","name":"INFOSYS LTD","isin":"INE009A01021"},
//               {"segment":"BSE_EQ","instrument_type":"EQ","trading_symbol":"RELIANCE",
//                "instrument_key":"BSE_EQ|INE002A01018","name":"RELIANCE INDUSTRIES","isin":"INE002A01018"}
//             ]
//             """;
//         service.loadFromJson(json);
//
//         assertThat(service.getEncodedInstrumentKey("RELIANCE")).hasValue("NSE_EQ%7CINE002A01018");
//         assertThat(service.getEncodedInstrumentKey("INFY")).hasValue("NSE_EQ%7CINE009A01021");
//     }
//
//     @Test
//     void symbolLookupIsCaseInsensitive() {
//         String json = """
//             [{"segment":"NSE_EQ","instrument_type":"EQ","trading_symbol":"HDFCBANK",
//               "instrument_key":"NSE_EQ|INE040A01034","name":"HDFC BANK","isin":"INE040A01034"}]
//             """;
//         service.loadFromJson(json);
//
//         assertThat(service.getEncodedInstrumentKey("hdfcbank")).hasValue("NSE_EQ%7CINE040A01034");
//         assertThat(service.getEncodedInstrumentKey("HDFCBANK")).hasValue("NSE_EQ%7CINE040A01034");
//     }
//
//     @Test
//     void unknownSymbolReturnsEmpty() {
//         service.loadFromJson("[]");
//         assertThat(service.getEncodedInstrumentKey("UNKNOWN")).isEmpty();
//     }
//
//     @Test
//     void filtersBseAndNonEquityInstruments() {
//         String json = """
//             [
//               {"segment":"BSE_EQ","instrument_type":"EQ","trading_symbol":"TCS",
//                "instrument_key":"BSE_EQ|INE467B01029","name":"TCS","isin":"INE467B01029"},
//               {"segment":"NSE_FO","instrument_type":"FUT","trading_symbol":"RELIANCE",
//                "instrument_key":"NSE_FO|INE002A01018","name":"RELIANCE FUT","isin":"INE002A01018"}
//             ]
//             """;
//         service.loadFromJson(json);
//
//         assertThat(service.getEncodedInstrumentKey("TCS")).isEmpty();
//         assertThat(service.getEncodedInstrumentKey("RELIANCE")).isEmpty();
//     }
//
//     @Test
//     void getAllSymbolsReturnsLoadedSymbols() {
//         String json = """
//             [
//               {"segment":"NSE_EQ","instrument_type":"EQ","trading_symbol":"RELIANCE",
//                "instrument_key":"NSE_EQ|INE002A01018","name":"RELIANCE","isin":"INE002A01018"},
//               {"segment":"NSE_EQ","instrument_type":"EQ","trading_symbol":"INFY",
//                "instrument_key":"NSE_EQ|INE009A01021","name":"INFOSYS","isin":"INE009A01021"}
//             ]
//             """;
//         service.loadFromJson(json);
//
//         assertThat(service.getAllSymbols()).containsExactlyInAnyOrder("RELIANCE", "INFY");
//     }
// }
