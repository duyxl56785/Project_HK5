# Release readiness

**Status: BLOCKED for final acceptance; PARTIALLY_COMPLETE for pre-final evidence.**

Live GitHub state and hosted CI are verified. PR #204's three historical review corrections are delivered on head `0bd80aaec8c78fdabd37517f2991e4c28ef6e88e`; TV2 review of that exact head remains pending. The controlling TV2 schedule is PR merge cutoff 20/11 12:00, main freeze 20/11 18:00, final UAT 21/11 and submission by 23/11. Frozen main SHA, clean-clone on another machine, final UAT, final figures and Finance settlement acceptance remain outstanding. Docker Engine is available, but the retained pre-final stack is incomplete (6 running / 9 exited; PostgreSQL exit 255; eight app containers have missing temporary secret bind mounts).
