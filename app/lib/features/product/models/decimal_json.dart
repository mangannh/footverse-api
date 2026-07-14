/// Parses a JSON decimal — a `BigDecimal` on the wire (dto-spec §9) — into a
/// [double] for display.
///
/// Accepts either a JSON number (`4.5`) or a quoted decimal string (`"4.5"`):
/// the backend serializes `BigDecimal` as a number by default, and the string
/// form is tolerated defensively so a Jackson `WRITE_BIGDECIMAL_AS_PLAIN`-style
/// change would not break the client. The client only displays these values and
/// never recomputes money or aggregates (dto-spec §1; business-rules).
double decimalFromJson(Object? value) {
  if (value is num) {
    return value.toDouble();
  }
  if (value is String) {
    return double.parse(value);
  }
  throw FormatException('Expected a decimal number or string', value);
}
