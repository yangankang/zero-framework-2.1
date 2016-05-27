package com.yoosal.mvc;

import com.yoosal.common.CollectionUtils;
import com.yoosal.common.ResourceUtils;
import com.yoosal.common.StringUtils;
import com.yoosal.common.scan.DefaultFrameworkScanClass;
import com.yoosal.common.scan.FrameworkScanClass;
import com.yoosal.mvc.annotation.APIController;
import com.yoosal.mvc.exception.MvcNotFoundConfigException;
import com.yoosal.mvc.exception.ParseTemplateException;
import com.yoosal.mvc.support.DefaultJavaScriptMapping;
import com.yoosal.mvc.support.JavaScriptMapping;
import com.yoosal.mvc.support.NormalViewResolver;
import com.yoosal.mvc.support.ViewResolver;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 这里是完成初始化工作,必须在启动的时候完成，读取配置，扫描类等
 */
public class EntryPointManager {

    /**
     * 所有的配置信息都在这里
     */
    private static final Map<String, Object> properties = new HashMap<String, Object>();

    //默认的类名的传参名称 比如：request.getParameter(DEFAULT_CLASS_KEY)
    private static final String DEFAULT_CLASS_KEY = "_class";
    //默认的方法名的传参名称 比如：request.getParameter(DEFAULT_METHOD_KEY)
    private static final String DEFAULT_METHOD_KEY = "_method";

    private static List classesInstanceFromProperties = null;
    private static List classesInstanceFromScan = null;
    
    static final String KEY_WRITE_PATH = "mvc.write.path";
    static final String KEY_API_PREFIX = "mvc.api.prefix";
    static final String KEY_DEBUGGER = "mvc.debugger";
    static final String KEY_FORMAT_EXCEPTION = "mvc.format.exception";
    static final String KEY_SCAN_PACKAGE = "mvc.scan.package";
    static final String KEY_API_CLASS = "mvc.class.";
    //http请求中代表方法的字段
    static final String KEY_METHOD_KEY = "mvc.key.method";
    //http请求中代表类名的字段
    static final String KEY_CLASS_KEY = "mvc.key.class";
    static final String KEY_REQUEST_URI = "mvc.request.uri";
    static final String KEY_REQUEST_RESTFUL = "mvc.request.restful";
    static final String KEY_COMPRESSOR_JS = "mvc.compressor.js";


    private static FrameworkScanClass frameworkScanClass = new DefaultFrameworkScanClass();
    private static ViewResolver viewResolver = null;

    /**
     * 将Properties文件中的配置转换成全局变量
     *
     * @param prop
     */
    public void setProperties(Properties prop, ServletContext servletContext) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        if (prop != null) {
            for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                properties.put((String) entry.getKey(), entry.getValue());
            }
        }
        classForName(prop);
        setScanClassAndInstance(servletContext);
    }

    private void classForName(Properties prop) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Enumeration enumeration = prop.propertyNames();
        List classes = new ArrayList();
        while (enumeration.hasMoreElements()) {
            Object object = enumeration.nextElement();
            if (object != null) {
                String key = String.valueOf(object);
                if (key.startsWith(KEY_API_CLASS)) {
                    String className = prop.getProperty(key);
                    classes.add(Class.forName(className).newInstance());
                }
            }
        }
        classesInstanceFromProperties = classes;
    }

    public void setClassesInstanceFromProperties(List list) {
        classesInstanceFromProperties = list;
    }

    public void setClassesInstanceFromScan(List list) {
        classesInstanceFromScan = list;
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    private static Object getProperty(String key) {
        if (CollectionUtils.isEmpty(properties)) {
            throw new MvcNotFoundConfigException("not found config!");
        }
        return properties.get(key);
    }

    public static String getWritePath() {
        return (String) getProperty(KEY_WRITE_PATH);
    }

    public static String getApiPrefix() {
        return (String) getProperty(KEY_API_PREFIX);
    }

    public static boolean isDebugger() {
        String isDebuggerString = String.valueOf(getProperty(KEY_DEBUGGER));
        if (isDebuggerString.equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }

    public static String getFormatException() {
        return (String) getProperty(KEY_FORMAT_EXCEPTION);
    }

    public static String getScanPackage() {
        return String.valueOf(getProperty(KEY_SCAN_PACKAGE));
    }

    public static List getConfigApiClass() {
        return classesInstanceFromProperties;
    }

    public static String getMethodKey() {
        String ck = (String) getProperty(KEY_METHOD_KEY);
        if (StringUtils.isNotBlank(ck)) {
            return ck;
        } else {
            return DEFAULT_METHOD_KEY;
        }
    }

    public static String getClassKey() {
        String ck = (String) getProperty(KEY_CLASS_KEY);
        if (StringUtils.isNotBlank(ck)) {
            return ck;
        } else {
            return DEFAULT_CLASS_KEY;
        }
    }

    public static String getRequestUri() {
        return (String) getProperty(KEY_REQUEST_URI);
    }

    public static boolean isRestful() {
        String isRestful = String.valueOf(getProperty(KEY_REQUEST_RESTFUL));
        if (isRestful.equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }

    public static ViewResolver getViewResolver() {
        if (viewResolver == null) {
            viewResolver = new NormalViewResolver();
        }
        return viewResolver;
    }

    public static List getApiClass() {
        List classes = new ArrayList();
        if (classesInstanceFromProperties != null) {
            classes.addAll(classesInstanceFromProperties);
        }
        if (classesInstanceFromScan != null) {
            classes.addAll(classesInstanceFromScan);
        }
        return classes;
    }

    public static boolean isCompressorJs() {
        if (String.valueOf(getProperty(KEY_COMPRESSOR_JS)).equalsIgnoreCase("true")) {
            return true;
        }
        return false;
    }

    public void setScanClassAndInstance(ServletContext servletContext) throws InstantiationException, IllegalAccessException {
        //扫描并实例所有的类
        if (StringUtils.isNotBlank(getScanPackage())) {
            classesInstanceFromScan = frameworkScanClass.getScanClassAndInstance(getScanPackage(), APIController.class);
        }
        afterInstanceClassMethod(servletContext);
    }

    /**
     * 读取完成所有的配置之后，执行解析类，JS生成等功能
     */
    private void afterInstanceClassMethod(ServletContext servletContext) {
        SceneFactory.cacheControllerInfo();
        this.produceJavaScriptMapping(servletContext);
    }

    public void produceJavaScriptMapping(ServletContext servletContext) {
        if (StringUtils.isNotBlank(getWritePath())) {
            JavaScriptMapping javaScriptMapping = new DefaultJavaScriptMapping();
            javaScriptMapping.setMethodParses(SceneFactory.getControllersInfo());
            String webRootPath = servletContext.getRealPath(getWritePath());
            try {
                if (isCompressorJs()) {
                    javaScriptMapping.generateToFile(webRootPath, true);
                } else {
                    javaScriptMapping.generateToFile(webRootPath);
                }
            } catch (ParseTemplateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
