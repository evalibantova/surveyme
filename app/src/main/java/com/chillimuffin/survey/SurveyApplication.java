package com.chillimuffin.survey;

import android.app.Application;
import android.content.Context;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraHttpSender;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

/**
 * Created by blasc on 27.01.2018.
 */
@AcraCore(buildConfigClass = BuildConfig.class)
@AcraHttpSender(httpMethod = HttpSender.Method.POST, uri = "http://remotelog.chillimuffin.com/acra.php")
public class SurveyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
        builder.setBuildConfigClass(BuildConfig.class).setReportFormat(StringFormat.JSON);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class).setEnabled(true);
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class).setMailTo("crashes@chillimuffin.com");
        builder.getPluginConfigurationBuilder(MailSenderConfigurationBuilder.class).setReportAsFile(true);
        ACRA.init(this, builder);
    }
}
