import 'package:material_ui/material_ui.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:text_scroll/text_scroll.dart';
import 'package:hiddify/core/theme/visual_effects.dart';

class CustomTextScroll extends ConsumerWidget {
  const CustomTextScroll(this.text, {super.key, this.style});

  final String text;
  final TextStyle? style;

  double calculateHeight(BuildContext context) {
    final TextPainter textPainter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      maxLines: 1,
      textScaler: MediaQuery.of(context).textScaler,
    )..layout();
    final height = textPainter.height;
    textPainter.dispose();
    return height;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (!VisualEffects.blurOf(context)) return Text(text, style: style, maxLines: 1, overflow: TextOverflow.ellipsis);
    return SizedBox(
      height: calculateHeight(context),
      child: TextScroll(
        text,
        mode: TextScrollMode.bouncing,
        velocity: const Velocity(pixelsPerSecond: Offset(30, 0)),
        pauseOnBounce: const Duration(seconds: 2),
        pauseBetween: const Duration(seconds: 2),
        style: style,
      ),
    );
  }
}
