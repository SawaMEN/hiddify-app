import 'package:material_ui/material_ui.dart';

/// Static vector atmosphere, isolated from foreground updates. No image decode,
/// frame ticker, full-screen blur or saveLayer is needed for this background.
class CyberBackground extends StatelessWidget {
  const CyberBackground({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Stack(
      fit: StackFit.expand,
      children: [
        Positioned.fill(
          child: IgnorePointer(
            child: ExcludeSemantics(
              child: RepaintBoundary(
                child: CustomPaint(painter: _AtmospherePainter(theme.colorScheme, theme.canvasColor)),
              ),
            ),
          ),
        ),
        child,
      ],
    );
  }
}

class _AtmospherePainter extends CustomPainter {
  const _AtmospherePainter(this.scheme, this.base);
  final ColorScheme scheme;
  final Color base;

  @override
  void paint(Canvas canvas, Size size) {
    final bounds = Offset.zero & size;
    canvas.drawRect(bounds, Paint()..color = base);
    if (base == Colors.black) return;
    canvas.drawRect(
      bounds,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(-.9, -.8),
          radius: 1.25,
          colors: [scheme.primary.withValues(alpha: .12), scheme.primary.withValues(alpha: 0)],
        ).createShader(bounds),
    );
    canvas.drawRect(
      bounds,
      Paint()
        ..shader = RadialGradient(
          center: const Alignment(.95, .35),
          radius: .9,
          colors: [scheme.secondary.withValues(alpha: .08), scheme.secondary.withValues(alpha: 0)],
        ).createShader(bounds),
    );
    final line = Paint()
      ..color = scheme.primary.withValues(alpha: .035)
      ..strokeWidth = 1;
    for (double x = 0; x < size.width; x += 64) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), line);
    }
    for (double y = 0; y < size.height; y += 64) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), line);
    }
  }

  @override
  bool shouldRepaint(_AtmospherePainter oldDelegate) => scheme != oldDelegate.scheme || base != oldDelegate.base;
}
