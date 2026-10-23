package dev.romulus_lanceues.tanaw_api.psgc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PsgcParentResolver Unit Tests")
class PsgcParentResolverTest {

    private PsgcParentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PsgcParentResolver();
    }

    @Nested
    @DisplayName("resolve - Region handling")
    class RegionHandling {

        @Test
        @DisplayName("should set parent to null for REGION without errors")
        void shouldSetParentToNull_forRegion() {
            Map<String, AreaType> typeByCode = Map.of(
                    "0100000000", AreaType.REGION
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .hasSize(1)
                    .containsEntry("0100000000", null);
        }

        @Test
        @DisplayName("should set parent to null for multiple REGIONs")
        void shouldSetParentToNull_forMultipleRegions() {
            Map<String, AreaType> typeByCode = Map.of(
                    "0100000000", AreaType.REGION,
                    "0200000000", AreaType.REGION,
                    "1300000000", AreaType.REGION
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .hasSize(3)
                    .containsEntry("0100000000", null)
                    .containsEntry("0200000000", null)
                    .containsEntry("1300000000", null);
        }
    }

    @Nested
    @DisplayName("resolve - Province and Special Area")
    class ProvinceAndSpecialAreaHandling {

        @Test
        @DisplayName("should resolve Region as parent for PROVINCE")
        void shouldResolveRegion_asParentForProvince() {
            String regionCode = "0100000000";
            String provinceCode = "0128000000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    provinceCode, AreaType.PROVINCE
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(regionCode, null)
                    .containsEntry(provinceCode, regionCode);
        }

        @Test
        @DisplayName("should resolve Region as parent for SPECIAL_AREA")
        void shouldResolveRegion_asParentForSpecialArea() {
            String regionCode = "1200000000";
            String specialAreaCode = "1298000000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    specialAreaCode, AreaType.SPECIAL_AREA
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(regionCode, null)
                    .containsEntry(specialAreaCode, regionCode);
        }
    }

    @Nested
    @DisplayName("resolve - City and Municipality")
    class CityAndMunicipalityHandling {

        @Test
        @DisplayName("should resolve Province as parent for MUNICIPALITY under a province")
        void shouldResolveProvince_asParentForMunicipality() {
            String regionCode = "0100000000";
            String provinceCode = "0128000000";
            String municipalityCode = "0128010000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    provinceCode, AreaType.PROVINCE,
                    municipalityCode, AreaType.MUNICIPALITY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(municipalityCode, provinceCode);
        }

        @Test
        @DisplayName("should resolve Province as parent for CITY under a province")
        void shouldResolveProvince_asParentForCity() {
            String regionCode = "0400000000";
            String provinceCode = "0421000000";
            String cityCode = "0421010000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    provinceCode, AreaType.PROVINCE,
                    cityCode, AreaType.CITY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(cityCode, provinceCode);
        }

        @Test
        @DisplayName("should fallback to Region as parent for independent CITY when Province is absent")
        void shouldFallbackToRegion_forIndependentCity() {
            String regionCode = "1300000000";
            String cityCode = "1339000000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    cityCode, AreaType.CITY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(cityCode, regionCode);
        }
    }

    @Nested
    @DisplayName("resolve - Sub-Municipality")
    class SubMunicipalityHandling {

        @Test
        @DisplayName("should resolve City as parent for SUB_MUNICIPALITY")
        void shouldResolveCity_asParentForSubMunicipality() {
            String regionCode = "1300000000";
            String cityCode = "1339000000";
            String subMunCode = "1339010000";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    cityCode, AreaType.CITY,
                    subMunCode, AreaType.SUB_MUNICIPALITY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(subMunCode, cityCode);
        }
    }

    @Nested
    @DisplayName("resolve - Barangay")
    class BarangayHandling {

        @Test
        @DisplayName("should resolve City/Municipality as parent for BARANGAY")
        void shouldResolveCityOrMunicipality_asParentForBarangay() {
            String regionCode = "0100000000";
            String provinceCode = "0128000000";
            String municipalityCode = "0128010000";
            String barangayCode = "0128010001";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    provinceCode, AreaType.PROVINCE,
                    municipalityCode, AreaType.MUNICIPALITY,
                    barangayCode, AreaType.BARANGAY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(barangayCode, municipalityCode);
        }

        @Test
        @DisplayName("should resolve Sub-Municipality as parent for BARANGAY under sub-municipality")
        void shouldResolveSubMunicipality_asParentForBarangay() {
            String regionCode = "1300000000";
            String cityCode = "1339000000";
            String subMunCode = "1339010000";
            String barangayCode = "1339010001";
            Map<String, AreaType> typeByCode = Map.of(
                    regionCode, AreaType.REGION,
                    cityCode, AreaType.CITY,
                    subMunCode, AreaType.SUB_MUNICIPALITY,
                    barangayCode, AreaType.BARANGAY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry(barangayCode, subMunCode);
        }
    }

    @Nested
    @DisplayName("resolve - Full Hierarchy")
    class FullHierarchyHandling {

        @Test
        @DisplayName("should resolve all parent-child relationships in a complete hierarchy")
        void shouldResolveCompleteHierarchy() {
            Map<String, AreaType> typeByCode = Map.of(
                    "0100000000", AreaType.REGION,
                    "0128000000", AreaType.PROVINCE,
                    "0128010000", AreaType.MUNICIPALITY,
                    "0128010001", AreaType.BARANGAY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.errors()).isEmpty();
            assertThat(resolution.parentByCode())
                    .containsEntry("0100000000", null)
                    .containsEntry("0128000000", "0100000000")
                    .containsEntry("0128010000", "0128000000")
                    .containsEntry("0128010001", "0128010000");
        }
    }

    @Nested
    @DisplayName("resolve - Error Handling and Edge Cases")
    class ErrorHandlingAndEdgeCases {

        @Test
        @DisplayName("should return empty parentByCode and empty errors for empty map")
        void shouldHandleEmptyMap() {
            PsgcParentResolver.Resolution resolution = resolver.resolve(Map.of());

            assertThat(resolution.parentByCode()).isEmpty();
            assertThat(resolution.errors()).isEmpty();
        }

        @Test
        @DisplayName("should report error when parent cannot be found")
        void shouldReportError_whenParentNotFound() {
            String orphanedProvinceCode = "0128000000";
            Map<String, AreaType> typeByCode = Map.of(
                    orphanedProvinceCode, AreaType.PROVINCE
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.parentByCode()).isEmpty();
            assertThat(resolution.errors())
                    .containsExactly("No parent found for " + orphanedProvinceCode + " (PROVINCE)");
        }

        @Test
        @DisplayName("should record errors for multiple areas with missing parents")
        void shouldRecordErrors_forMultipleOrphanedAreas() {
            Map<String, AreaType> typeByCode = Map.of(
                    "0128000000", AreaType.PROVINCE,
                    "0250000000", AreaType.PROVINCE
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.parentByCode()).isEmpty();
            assertThat(resolution.errors())
                    .hasSize(2)
                    .contains(
                            "No parent found for 0128000000 (PROVINCE)",
                            "No parent found for 0250000000 (PROVINCE)"
                    );
        }

        @Test
        @DisplayName("should not match candidate if candidate rank is not strictly lower than area rank")
        void shouldNotMatchCandidate_whenRankIsNotLower() {
            String barangayCode = "0128010001";
            String candidateCityCode = "0128010000";

            // If candidateCityCode is erroneously typed as BARANGAY (rank 5), it cannot be a parent
            Map<String, AreaType> typeByCode = Map.of(
                    barangayCode, AreaType.BARANGAY,
                    candidateCityCode, AreaType.BARANGAY
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.parentByCode()).isEmpty();
            assertThat(resolution.errors())
                    .contains(
                            "No parent found for " + barangayCode + " (BARANGAY)",
                            "No parent found for " + candidateCityCode + " (BARANGAY)"
                    );
        }

        @Test
        @DisplayName("should ignore candidate when candidate code equals self code")
        void shouldIgnoreCandidate_whenCandidateEqualsSelfCode() {
            Map<String, AreaType> typeByCode = Map.of(
                    "0128000000", AreaType.PROVINCE
            );

            PsgcParentResolver.Resolution resolution = resolver.resolve(typeByCode);

            assertThat(resolution.parentByCode()).isEmpty();
            assertThat(resolution.errors())
                    .containsExactly("No parent found for 0128000000 (PROVINCE)");
        }
    }
}
