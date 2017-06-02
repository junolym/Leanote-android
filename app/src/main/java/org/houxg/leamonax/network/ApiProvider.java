package org.houxg.leamonax.network;

import com.elvishew.xlog.XLog;
import com.facebook.stetho.okhttp3.StethoInterceptor;

import org.houxg.leamonax.BuildConfig;
import org.houxg.leamonax.model.Account;
import org.houxg.leamonax.network.api.AuthApi;
import org.houxg.leamonax.network.api.NoteApi;
import org.houxg.leamonax.network.api.NotebookApi;
import org.houxg.leamonax.network.api.UserApi;
import org.houxg.leamonax.utils.SharedPreferenceUtils;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

public class ApiProvider {

    private Retrofit mApiRetrofit;

    private static final String DISABLE_SSL_CHECK = "disable_ssl_check";

    private static class SingletonHolder {
        private final static ApiProvider INSTANCE = new ApiProvider();
    }

    public static ApiProvider getInstance() {
        Account account = Account.getCurrent();
        if (account != null && SingletonHolder.INSTANCE.mApiRetrofit == null) {
            SingletonHolder.INSTANCE.init(account.getHost());
        }
        return SingletonHolder.INSTANCE;
    }

    private ApiProvider() {
    }

    public void init(String host) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

//        if (SharedPreferenceUtils.read(SharedPreferenceUtils.CONFIG, DISABLE_SSL_CHECK, false)) {
            X509TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { tm }, null);
                builder.sslSocketFactory(sslContext.getSocketFactory(), tm);
            } catch(Exception e) {
                XLog.e(e.getMessage());
            }
//        }

        builder.addNetworkInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();
                        HttpUrl url = request.url();
                        String path = url.encodedPath();
                        HttpUrl newUrl = url;
                        if (shouldAddTokenToQuery(path)) {
                            newUrl = url.newBuilder()
                                    .addQueryParameter("token", Account.getCurrent().getAccessToken())
                                    .build();
                        }
                        Request newRequest = request.newBuilder()
                                .url(newUrl)
                                .build();
                        return chain.proceed(newRequest);
                    }
                });
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                @Override
                public void log(String message) {
                    XLog.d(message);
                }
            });
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addNetworkInterceptor(interceptor);
            builder.addNetworkInterceptor(new StethoInterceptor());
        }
        OkHttpClient client = builder.build();
        mApiRetrofit = new Retrofit.Builder()
                .baseUrl(host + "/api/")
                .client(client)
                .addConverterFactory(new LeaResponseConverterFactory())
                .build();
    }

    private static boolean shouldAddTokenToQuery(String path) {
        return !path.endsWith("/api/auth/login")
                && !path.endsWith("/api/auth/register");
    }

    public AuthApi getAuthApi() {
        return mApiRetrofit.create(AuthApi.class);
    }

    public NoteApi getNoteApi() {
        return mApiRetrofit.create(NoteApi.class);
    }

    public UserApi getUserApi() {
        return mApiRetrofit.create(UserApi.class);
    }

    public NotebookApi getNotebookApi() {
        return mApiRetrofit.create(NotebookApi.class);
    }

}
