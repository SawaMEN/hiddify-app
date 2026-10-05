import 'package:dart_mappable/dart_mappable.dart';
import 'package:dartx/dartx.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:hiddify/core/localization/translations.dart';

part 'optional_range.mapper.dart';

@MappableClass()
class OptionalRange with OptionalRangeMappable {
  const OptionalRange({this.min, this.max});

  final int? min;
  final int? max;

  String format() => [min, max].whereNotNull().join("-");
  String present(TranslationsEn t) => format().isEmpty ? t.common.notSet : format();

  factory OptionalRange.parse(String input, {bool allowEmpty = false}) {
    final value = input.trim();
    if (value.isEmpty && allowEmpty) return const OptionalRange();
    if (!RegExp(r'^\d+(?:-\d+)?$').hasMatch(value)) throw FormatException('Invalid range', input);
    final parts = value.split('-');
    final min = int.parse(parts.first);
    final max = parts.length == 2 ? int.parse(parts.last) : null;
    if (max != null && min > max) throw FormatException('Reversed range', input);
    return OptionalRange(min: min, max: max);
  }

  static OptionalRange? tryParse(String input, {bool allowEmpty = false}) {
    try {
      return OptionalRange.parse(input, allowEmpty: allowEmpty);
    } catch (_) {
      return null;
    }
  }
}

class OptionalRangeJsonConverter implements JsonConverter<OptionalRange, String> {
  const OptionalRangeJsonConverter();

  @override
  OptionalRange fromJson(String json) => OptionalRange.parse(json, allowEmpty: true);

  @override
  String toJson(OptionalRange object) => object.format();
}
