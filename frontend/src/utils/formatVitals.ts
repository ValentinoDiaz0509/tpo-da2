export const formatVitalValue = (
  value: number | null | undefined,
  decimals = 2,
): string =>
  value != null && !Number.isNaN(value) ? value.toFixed(decimals) : '--';

export const formatBloodPressure = (
  systolic: number | null | undefined,
  diastolic: number | null | undefined,
  decimals = 2,
): string =>
  `${formatVitalValue(systolic, decimals)}/${formatVitalValue(diastolic, decimals)}`;
