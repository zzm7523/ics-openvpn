/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import de.blinkt.xp.openvpn.BuildConfig;
import de.blinkt.xp.openvpn.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import de.blinkt.openvpn.core.OpenVPNLaunchHelper;
import de.blinkt.openvpn.core.Preferences;

public class MailUtils {

    private static final String username = "support@xxxx.xxx";
    private static final String password = "xxxxxxxx";
    private static final String mail_addr = "support@xxxx.xxx";
    private static final String smtp_addr = "aaa.xxxx.xxx";
    private static final int smtp_port = 465;

    public static void sendMail(@NonNull Context context) throws MessagingException, IOException {
        Properties props = new Properties();
        // Ignore server cert verify error
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.ssl.enable", true);
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.host", smtp_addr);
        props.put("mail.smtp.port", smtp_port);

        Session session = Session.getDefaultInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

        MimeMessage message = new MimeMessage(session);

        Address fromAddress = new InternetAddress(mail_addr);
        message.setFrom(fromAddress);

        Address toAddress = new InternetAddress(mail_addr);
        message.addRecipient(javax.mail.Message.RecipientType.TO, toAddress);

        String subject = context.getString(R.string.diagnostic_info);
        message.setSubject(subject);

        Multipart multipart = new MimeMultipart();
        BodyPart contentPart = new MimeBodyPart();

        StringWriter writer = new StringWriter();
        // 系统和APP信息
        try {
            writer.write(collectAppAndSystemInfo(context));
        } catch (Exception ex) {
            writer.write("提取App和系统信息失败!\n");
            ex.printStackTrace(new PrintWriter(writer));
        } finally {
            writer.write("\n\n\n");
        }

        // APP这次启动后缓存的实时日志, !!不需要, 日志已实时写入*.log文件
/*
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        for (LogItem li : VpnStatus.getLogBufferAll()) {
            String logtime = dateFormat.format(new Date(li.getLogtime()));
            writer.write(logtime + " " + li.getString(context) + "\n");
        }
*/

        contentPart.setContent(writer.toString(), "text/plain;charset=UTF-8");
        multipart.addBodyPart(contentPart);

        // 最后一次连接OpenVPN配置文件
        addAttachFile(multipart, OpenVPNLaunchHelper.getConfigFile(context));

        // 最后一次连接 日志文件
        addAttachFile(multipart, new File(context.getCacheDir(), "OpenVPN_front.log"));
        addAttachFile(multipart, new File(context.getCacheDir(), "OpenVPN_console.log"));
        addAttachFile(multipart, new File(context.getCacheDir(), "OpenVPN_management.log"));

        // 最近5个Crash文件
        File crashDir = new File(context.getCacheDir(), "crash");
        File[] crashFiles = crashDir.listFiles();
        if (crashFiles != null && crashFiles.length > 0) {
            Arrays.sort(crashFiles, (a, b) -> (int) (b.lastModified() - a.lastModified()));
            for (int i = 0; i < 5 && i < crashFiles.length; i++)
                addAttachFile(multipart, crashFiles[i]);
        }

        message.setContent(multipart);
        message.saveChanges();

        Transport transport = session.getTransport("smtp");
        transport.connect(smtp_addr, username, password);

        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
    }

    private static String collectAppAndSystemInfo(@NonNull Context context) {
        StringWriter writer = new StringWriter();

        // 系统信息
        String brand = android.os.Build.BRAND;
        writer.write("Brand: " + (brand == null ? "" : brand) + "\n");
        String model = android.os.Build.MODEL;
        writer.write("Model: " + (model == null ? "" : model) + "\n");
        String version = android.os.Build.VERSION.RELEASE;
        writer.write("Android version: " + (version == null ? "" : version) + "\n");
        writer.write("Base band: " + getBaseBand() + "\n\n");
        // 内核版本是多行信息
        writer.write("Kernel Version:\n " + getKernelVersion() + "\n");

        // APP信息
        writer.write("OpenVPN version: " + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE + "\n");
        writer.write("OpenVPN build for: " + OpenVPNUtils.buildFor(context) + "\n");

        return writer.toString();
    }

    private static void addAttachFile(@NonNull Multipart multipart, @NonNull File attachFile)
            throws MessagingException, UnsupportedEncodingException {
        if (attachFile.exists()) {
            BodyPart attachPart = new MimeBodyPart();
            attachPart.setDataHandler(new DataHandler(new FileDataSource(attachFile)));
            // MimeUtility.encodeWord可以避免文件名乱码
            attachPart.setFileName(MimeUtility.encodeWord(attachFile.getName()));
            multipart.addBodyPart(attachPart);
        }
    }

    /**
     * 获取基带版本
     */
    private static String getBaseBand() {
        try {
            Class clazz = Class.forName("android.os.SystemProperties");
            Object invoker = clazz.newInstance();
            Method m = clazz.getMethod("get", String.class, String.class);
            Object result = m.invoke(invoker, "gsm.version.baseband", "no message");
            return result == null ? "" : result.toString();

        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * 获取内核版本
     */
    public static String getKernelVersion() {
        try (InputStream input = new FileInputStream("/proc/version")) {
            return IOUtils.readStreamAsString(input, 2 * 1024);

        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }

}
