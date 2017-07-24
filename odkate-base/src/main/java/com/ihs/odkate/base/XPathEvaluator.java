package com.ihs.odkate.base;

import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

//http://leakfromjavaheap.blogspot.com/2014/12/xpath-evaluation-performance-tweaks.html
public class XPathEvaluator {
    private static final String DTM_MANAGER_NAME = "com.sun.org.apache.xml.internal.dtm.DTMManager";
    private static final String DTM_MANAGER_VALUE = "com.sun.org.apache.xml.internal.dtm.ref.DTMManagerDefault";
    static
    {
        // performance improvement: https://issues.apache.org/jira/browse/XALANJ-2540
        System.setProperty(DTM_MANAGER_NAME, DTM_MANAGER_VALUE);
    }
    private static final ThreadLocal<XPathFactory> XPATH_FACTORY = new ThreadLocal<XPathFactory>()
    {
        @Override
        protected XPathFactory initialValue()
        {
            return XPathFactory.newInstance();
        }
    };

    private static XPath xpath;

    // Xpath is not thread safe
    public static synchronized XPath getXpath() {
        if (xpath == null){
            xpath = XPATH_FACTORY.get().newXPath();
        }
        return xpath;
    }

    public static synchronized Object query(String xPathExpression, Node document, QName resultType)
    {
        try
        {
            XPathExpression expression = getXpath().compile(xPathExpression);
            return expression.evaluate(document, resultType);
        }
        catch (XPathExpressionException e){
            throw new IllegalStateException("Error while executing XPath evaluation!", e);
        }
    }
} 