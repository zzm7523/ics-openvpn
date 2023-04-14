/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.utils.StringUtils;

/**
 * Created by arne on 14.03.18.
 */

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class TestConfigGenerator {

    @Test
    public void testAuthRetryGen() throws Exception {
        /*Context mc = mock(Context.class);
        PackageManager mpm = mock(PackageManager.class);

        PackageInfo mpi = new PackageInfo();
        mpi.versionCode = 177;
        mpi.versionName = "foo";

        when(mc.getCacheDir()).thenReturn(new File("/j/unit/test/"));
        when(mc.getPackageName()).thenReturn("de.blinkt.openvpn");
        when(mc.getPackageManager()).thenReturn(mpm);
        when(mpm.getPackageInfo(eq("de.blinkt.openvpn"),eq(0))).thenReturn(mpi);*/


        VpnProfile vp = new VpnProfile("test");
        vp.setAuthenticationType(VpnProfile.TYPE_USERPASS);
        String config = vp.generateConfig(RuntimeEnvironment.application, null);
        Assert.assertTrue(config.contains("\nauth-retry nointeract\n"));
        for (Connection connection : vp.getConnections())
            Assert.assertTrue(connection.getProxyType() == Connection.ProxyType.NONE);
    }

    @Test
    public void testEscape() {
        String uglyPassword = "^OrFg1{G^SS8b4J@B$Y1Dr\\GwG-dw3aBJ/R@WI*doCVP',+:>zjqC[&b6[8=KL:`{l&:i!_4*npE?4k2c^(n>9Tjp~u2Z]l8(y&Gg<-cwR2k=yKK:-%f-ezQ\"^g)[d,kbsu$cqih\\wA~on$~)QSODtip2cd,+->qv,roF*9>6q:lTepm=r?Y-+(K]ERGn\"+AiLj<(R_'BOg:vsh0wh]BQ-PVo534;l%R*FF!+,$?Q00%839(k?E!x0R[Lx6qK\\&";
        String escapedUglyPassword = StringUtils.escape(uglyPassword);
    }

}
