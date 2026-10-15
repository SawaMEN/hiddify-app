# Stable scrolling and automatic profile settings

The four native bottom menus use `NativeModalSheet`: a bottom-aligned dialog with
a fixed viewport derived from window bounds, independent of content size and
scroll position. Body lists own normal scroll/fling gestures; there are no drag
anchors or offset-dependent sheet insets. System bars and the keyboard are
accounted for once. Close, Back and outside-tap dismissal preserve existing
import cancellation restrictions. Elastic overscroll is disabled; picker dialogs
use bounded lazy lists.

The active profile network menu has no text inputs, including advanced settings.
Ready choices cover subscription clients, browser TLS fingerprints, fragmentation,
ALPN, TLS version policy, SNI transmission, fallback delay and client metadata.
Browser User-Agent values are compatibility templates, not detected installed
browser versions. Client metadata uses the actual app version.

TLS targets and SNI names come from the source profile. Arbitrary certificate
names, authentication tokens and unsupported fingerprints are never generated.
Known provider authorization headers survive metadata/format changes and the
automatic reset. Unknown saved values stay as read-only choices until the user
selects a supported replacement.

Choosing a different target clears the previous SNI and incompatible options.
REALITY keeps its uTLS/server requirements; QUIC inherits its protocol settings.
System TLS without uTLS and explicit SNI transmission controls require a JSON
profile. Certificate validation and pins are preserved. Profile refresh continues
to keep local overrides.

Validation: production Android/Compose compilation, the JVM regression suite,
resource/project checks, and fifteen configuration checks on the shipped stable
engine covering every fingerprint and the new SNI/native-TLS/fragment variants.
These checks do not replace interactive Android-device testing or guarantee that
a remote server accepts a chosen fingerprint or SNI mode.
