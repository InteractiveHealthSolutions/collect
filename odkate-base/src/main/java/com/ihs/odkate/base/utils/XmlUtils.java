package com.ihs.odkate.base.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

//http://leakfromjavaheap.blogspot.com/2014/12/xpath-evaluation-performance-tweaks.html
public class XmlUtils {
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

    public static Document parseXml(String xml) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new StringReader(xml)));
        return doc;
    }

    public static Document readXml(String path) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new File(path));
        return doc;
    }

    public static void writeXml(Document doc, String path) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");

        // initialize StreamResult with File object to save to file where odk looks for forms
        StreamResult result = new StreamResult(path);
        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);
    }

    public static String getUniqueValueForPath(String path, Node doc){
        Node node = (Node) XmlUtils.query(path, doc, XPathConstants.NODE);
        if (node == null){
            return null;
        }

        return node.getTextContent();
    }

    public static String getUniqueAttributeForPath(String path, String attribute, Node doc){
        Node n = (Node) XmlUtils.query(path, doc, XPathConstants.NODE);
        if (n == null){
            return null;
        }

        return n.hasAttributes()&&n.getAttributes().getNamedItem(attribute)!=null?
                n.getAttributes().getNamedItem(attribute).getNodeValue():null;
    }

    public static Node getNextChild(Node el) {
        for (int i = 0; i < el.getChildNodes().getLength(); i++){
            if (el.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE){
                return el.getChildNodes().item(i);
            }
        }
        return null;
    }
} 