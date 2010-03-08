package org.glassfish.admingui.devtests;

import org.junit.Test;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertTrue;

public class ConnectorsTest extends BaseSeleniumTestClass {
    @Test
    public void testConnectorResources() {
        String testPool = generateRandomString();
        String testConnector = generateRandomString();

        openAndWait("/jca/connectorConnectionPools.jsf", "Connector Connection Pools");

        // Create new connection connection pool
        selenium.click("propertyForm:poolTable:topActionsGroup1:newButton");
        waitForPageLoad("New Connector Connection Pool (Step 1 of 2)");
        selenium.type("propertyForm:propertySheet:generalPropertySheet:jndiProp:name", testPool);
        selenium.select("propertyForm:propertySheet:generalPropertySheet:resAdapterProp:db", "label=jmsra");
        waitForElementContentNotEqualTo("propertyForm:propertySheet:generalPropertySheet:connectionDefProp:db", "\\\\n");
        try {
            sleep(500);
        } catch (InterruptedException e) {
            
        }
        selenium.click("propertyForm:title:topButtons:nextButton");
        waitForPageLoad("New Connector Connection Pool (Step 2 of 2)");

        selenium.select("propertyForm:propertySheet:poolPropertySheet:transprop:trans", "label=NoTransaction");
        selenium.click("propertyForm:propertyContentPage:topButtons:finishButton");

        // Verify pool creation
        waitForPageLoad("Click New to create a new connector connection pool.");
        assertTrue(selenium.isTextPresent(testPool));

        // Create new connector resource which uses this new pool
        selenium.click("treeForm:tree:resources:Connectors:connectorResources:connectorResources_link");
        waitForPageLoad("A connector resource is a program object");
        selenium.click("propertyForm:resourcesTable:topActionsGroup1:newButton");
        waitForPageLoad("New Connector Resource");

        selenium.type("propertyForm:propertySheet:propertSectionTextField:jndiTextProp:jnditext", testConnector);
        selenium.click("propertyForm:propertyContentPage:topButtons:newButton");
        waitForPageLoad("A connector resource is a program object that provides");

        // Disable resource
        assertTrue(selenium.isTextPresent(testConnector));
        selectTableRowByValue("propertyForm:resourcesTable", testConnector);
        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button3");
        waitForPageLoad("false");

        // Enable resource
        selectTableRowByValue("propertyForm:resourcesTable", testConnector);
        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button2");
        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button2");
        waitForPageLoad("true");

        // Delete connector resource
        selenium.chooseOkOnNextConfirmation();
        selectTableRowByValue("propertyForm:resourcesTable", testConnector);
        selenium.click("propertyForm:resourcesTable:topActionsGroup1:button1");
        selenium.getConfirmation();
        waitForPageLoad(testConnector, true);

        // Delete connector connection pool
        selenium.chooseOkOnNextConfirmation();
        selenium.click("treeForm:tree:resources:Connectors:connectorConnectionPools:connectorConnectionPools_link");
        waitForPageLoad("Click New to create a new connector connection pool.");
        selectTableRowByValue("propertyForm:poolTable", testPool);
        selenium.click("propertyForm:poolTable:topActionsGroup1:button1");
        selenium.getConfirmation();
        waitForPageLoad(testPool, true);
    }
}