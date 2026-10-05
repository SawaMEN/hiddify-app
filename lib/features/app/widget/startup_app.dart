import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

/// Renders before preferences, translations and platform plugins load.
/// Animations never delay initialization or access those services.
class StartupApp extends StatelessWidget {
  const StartupApp({required this.initialization, super.key});
  final Future<Widget> initialization;

  @override
  Widget build(BuildContext context) => FutureBuilder<Widget>(
    future: initialization,
    builder: (context, snapshot) {
      if (snapshot.hasData) return snapshot.requireData;
      return MaterialApp(
        debugShowCheckedModeBanner: false,
        home: Scaffold(
          backgroundColor: const Color(0xFF020506),
          body: snapshot.hasError ? _StartupFailure(error: snapshot.error!) : const _StartupScene(),
        ),
      );
    },
  );
}

class _StartupScene extends StatefulWidget {
  const _StartupScene();
  @override
  State<_StartupScene> createState() => _StartupSceneState();
}

class _StartupSceneState extends State<_StartupScene> with TickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _entrance;
  late final AnimationController _breathing;
  late final Animation<double> _reveal;
  bool _reducedMotion = false;
  bool _foreground = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _entrance = AnimationController(vsync: this, duration: const Duration(milliseconds: 1100));
    _breathing = AnimationController(vsync: this, duration: const Duration(milliseconds: 3200));
    _reveal = CurvedAnimation(parent: _entrance, curve: Curves.easeOutCubic);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _reducedMotion = MediaQuery.disableAnimationsOf(context);
    _syncAnimations();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    _syncAnimations();
  }

  void _syncAnimations() {
    if (_reducedMotion || !_foreground) {
      _entrance.stop();
      _breathing.stop();
      if (_reducedMotion) _entrance.value = 1;
    } else {
      if (!_entrance.isCompleted) _entrance.forward();
      if (!_breathing.isAnimating) _breathing.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _entrance.dispose();
    _breathing.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final english = WidgetsBinding.instance.platformDispatcher.locale.languageCode == 'en';
    return Stack(
      fit: StackFit.expand,
      children: [
        const RepaintBoundary(child: CustomPaint(painter: _StartupBackdrop())),
        SafeArea(child: Center(child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 380),
            child: FadeTransition(opacity: _reveal, child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                AnimatedBuilder(
                  animation: _breathing,
                  child: Image.asset('assets/images/logo.png', width: 176, height: 176, semanticLabel: 'VetrOFF'),
                  builder: (context, child) => Container(
                    decoration: BoxDecoration(shape: BoxShape.circle, boxShadow: [BoxShadow(
                      color: const Color(0xFF26C6F5).withValues(alpha: .08 + _breathing.value * .08),
                      blurRadius: 36 + _breathing.value * 16,
                      spreadRadius: 4,
                    )]),
                    child: child,
                  ),
                ),
                const SizedBox(height: 28),
                SlideTransition(
                  position: Tween<Offset>(begin: const Offset(0, .35), end: Offset.zero).animate(_reveal),
                  child: ShaderMask(
                    shaderCallback: (bounds) => const LinearGradient(
                      colors: [Color(0xFFF3F8FA), Color(0xFF72DAFF), Color(0xFF229EFF)],
                    ).createShader(bounds),
                    blendMode: BlendMode.srcIn,
                    child: const Text('VetrOFF Client', textAlign: TextAlign.center, style: TextStyle(
                      fontFamily: 'Manrope', fontSize: 30, fontWeight: FontWeight.w800, color: Colors.white,
                    )),
                  ),
                ),
                const SizedBox(height: 36),
                SizedBox(width: 22, height: 22, child: CircularProgressIndicator(
                  value: _reducedMotion ? .75 : null, strokeWidth: 2, color: const Color(0xFF55D3FF),
                )),
                const SizedBox(height: 16),
                Text(english ? 'Starting' : 'Запуск', style: const TextStyle(
                  fontFamily: 'Manrope', fontSize: 15, color: Color(0xFF9BAEBB), letterSpacing: 2,
                )),
              ],
            )),
          ),
        ))),
      ],
    );
  }
}

class _StartupBackdrop extends CustomPainter {
  const _StartupBackdrop();
  @override
  void paint(Canvas canvas, Size size) {
    final bounds = Offset.zero & size;
    canvas.drawRect(bounds, Paint()..shader = const RadialGradient(
      center: Alignment(0, -.4), radius: .85, colors: [Color(0xFF09232E), Color(0xFF020506)],
    ).createShader(bounds));
    final line = Paint()..color = const Color(0x092CC9FF)..strokeWidth = 1;
    for (double x = 0; x < size.width; x += 56) {
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), line);
    }
    for (double y = 0; y < size.height; y += 56) {
      canvas.drawLine(Offset(0, y), Offset(size.width, y), line);
    }
    final orbit = Paint()..color = const Color(0x1446C8FF)..style = PaintingStyle.stroke..strokeWidth = 1;
    final center = Offset(size.width * .5, size.height * .36);
    final radius = math.min(size.width, size.height) * .72;
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), -.6, 1.7, false, orbit);
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius * 1.18), 2.5, 1.4, false, orbit);
  }
  @override
  bool shouldRepaint(_StartupBackdrop oldDelegate) => false;
}

class _StartupFailure extends StatelessWidget {
  const _StartupFailure({required this.error});
  final Object error;
  @override
  Widget build(BuildContext context) {
    final english = WidgetsBinding.instance.platformDispatcher.locale.languageCode == 'en';
    const style = TextStyle(color: Color(0xFF9BAEBB));
    return SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.error_outline, size: 48, color: Color(0xFF55D3FF)),
        const SizedBox(height: 16),
        Text(english ? 'Unable to start VetrOFF Client' : 'Не удалось запустить VetrOFF Client',
          style: const TextStyle(color: Colors.white)),
        const SizedBox(height: 16),
        SelectableText('$error', textAlign: TextAlign.center, style: style),
        const SizedBox(height: 16),
        Text(english ? 'Close and reopen the app to retry.' : 'Закройте и снова откройте приложение.', style: style),
      ]),
    )));
  }
}
