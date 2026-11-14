# hev-socks5-tunnel registers its native methods on this exact class via JNI (RegisterNatives):
# R8 must not rename the class or its methods.
-keep class app.xvpn.vpn.Tun2Socks { *; }
