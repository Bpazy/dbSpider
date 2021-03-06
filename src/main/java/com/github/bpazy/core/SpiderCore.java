package com.github.bpazy.core;

import com.github.bpazy.utils.Helper;
import com.github.bpazy.utils.QueueAndRedis;
import com.github.bpazy.utils.SqlFactory;
import com.github.kevinsawicki.http.HttpRequest;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * Created by Ziyuan.
 * 2016/12/6 15:10
 */
public abstract class SpiderCore<T> {
    private static final int TIMEOUT = 1000 * 5;
    private String target;
    private QueueAndRedis queueAndRedis;
    private SessionFactory factory = SqlFactory.getSessionFactory();

    public SpiderCore(String target, QueueAndRedis queueAndRedis) {
        this.target = target;
        this.queueAndRedis = queueAndRedis;
    }

    void run() {
        String body;
        try {
            body = HttpRequest
                    .get(target)
                    .header("Cookie", "bid=" + Helper.getRandomString(11))
                    .readTimeout(TIMEOUT)
                    .connectTimeout(TIMEOUT)
                    .body();
        } catch (HttpRequest.HttpRequestException e) {
            queueAndRedis.redisSetRemove(target);
            queueAndRedis.queuePut(target);
            System.err.println("Failed at: " + target);
            return;
        }
        Document doc = Jsoup.parse(body);
        save(doc);
        Elements hrefElements = doc.select(relatedUrlSelect());
        hrefElements.forEach(element -> {
            String href = element.attr("href");
            if (!"".equals(href)) {
                queueAndRedis.queuePut(href);
            }
        });
    }

    private void save(Document doc) {
        T model = getModel(doc);
        if (isValid(model)) {
            Session session = factory.openSession();
            session.beginTransaction();
            session.save(model);
            session.getTransaction().commit();
        }
    }

    /**
     * 相关的URL
     *
     * @return 选择器，例如: "div#recommendations div dl dt a" 电影页面的相关电影
     */
    protected abstract String relatedUrlSelect();

    /**
     * 用于判断model是否合法
     *
     * @param model model
     * @return 合法返回true
     */
    protected abstract boolean isValid(T model);

    /**
     * 根据Document对象生成model
     *
     * @param doc Document对象
     * @return model对象
     */
    protected abstract T getModel(Document doc);

    /**
     * @param doc    Document对象
     * @param select 选择器
     * @return 选择器对应的标签的值
     */
    protected String infoSelect(Document doc, String select) {
        return doc.select(select).text();
    }
}
