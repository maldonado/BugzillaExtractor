/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lightningbug;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.httpclient.HttpClient;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;

/**
 *
 * @author Administrator
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    private Connection connection;
    private XmlRpcClient rpcClient;
    private List<MyBug> bugList = null;

    public Connection getConn() {
        return connection;
    }

    public Main() throws ClassNotFoundException, SQLException, MalformedURLException, XmlRpcException, IOException {
        Properties props = getProperties("bugzilla.properties");
        setupDBConn(props);
        setupRPCClient(props);
        loginBugzilla(props);
    }

    private Properties getProperties(String propsFileName) throws IOException {
        Properties props = new Properties(System.getProperties());
        props.load(new FileInputStream(propsFileName));
        return props;
    }

    private void loginBugzilla(Properties props) throws XmlRpcException {
        // map of the login data
        Map loginMap = new HashMap();
        loginMap.put("login", props.getProperty("bugzilla.wsuser"));
        loginMap.put("password", props.getProperty("bugzilla.wspw"));
        loginMap.put("rememberlogin", "Bugzilla_remember");
        // login to bugzilla
        Object loginResult = rpcClient.execute("User.login", new Object[]{loginMap});
    }

    private void setupDBConn(Properties props) throws IOException, ClassNotFoundException, SQLException {
        System.out.println("getting database connection...");
        String driverName = props.getProperty("bugzilla.driverName");
        Class.forName(driverName);
        String url = props.getProperty("bugzilla.dburl");
        String username = props.getProperty("bugzilla.dbuser");
        String password = props.getProperty("bugzilla.dbpw");
        connection = DriverManager.getConnection(url, username, password);
    }

    private void setupRPCClient(Properties props) throws MalformedURLException {
        System.out.println("getting bugzilla connection...");
        HttpClient httpClient = new HttpClient();
        rpcClient = new XmlRpcClient();
        XmlRpcCommonsTransportFactory factory = new XmlRpcCommonsTransportFactory(rpcClient);
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();

        factory.setHttpClient(httpClient);
        rpcClient.setTransportFactory(factory);
        String wsurl = props.getProperty("bugzilla.wsurl");
        config.setServerURL(new URL(wsurl));

        rpcClient.setConfig(config);
    }

    public static void main(String[] args) throws Exception {
        Main m = new Main();
        Statement st = m.getConn().createStatement();
        System.out.println("searching bugzilla...");
        Map bugSearch = new HashMap();
//        Object[] products = {"Platform"};
        //Object[] components = {"All", "Build", "Core"}; //list all component. ommit to include all components
//        Object[] status = {"CLOSED"};
        Object[] id = {"49380","211447","43952","122442","264238","112774","48131","298510","81140","49383","55435","357547","183463","209706","311582","123375","102780","371233","34548","252677","259687","311617","57455","402028","153500","209872","48141","61553","317706","360044","212389","318759","209872","350103","24344","401030","40255","209836","61270","109878","180921","213297","72566","264238","53565","243441","250794","227043"};
//        bugSearch.put("product", products);
        //bugSearch.put("component", components);  //comment this line to include all component
//        bugSearch.put("status", status);
        bugSearch.put("id", id);
      
        m.bugList = m.searchBugs(bugSearch);
        List<Integer> idList = new ArrayList();
        System.out.println("building arraylist...");
        
        //build array of ids for retrieving history details for each bug id
        for (MyBug mb : m.bugList) {
            idList.add(mb.getId());
        }

        Object[] ids = idList.toArray();
        Map bugMap = new HashMap();
        bugMap.put("ids", ids);
        System.out.println("processing bugs...");
//        m.processHistory(bugMap);
        String sql = "INSERT INTO httpdbugs (bug_id, product, component, status, resolution, reported, closed, summary, assignee, lastChangeTime) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement ps = m.getConn().prepareStatement(sql);
        System.out.println("building batch update statment...");
        
        for (MyBug mb : m.bugList) {
            ps.setLong(1, mb.getId());
            ps.setString(2, mb.getProduct());
            ps.setString(3, mb.getComponent());
            ps.setString(4, mb.getStatus());
            ps.setString(5, mb.getResolution());
            ps.setTimestamp(6, mb.getReported());
            ps.setTimestamp(7, mb.getClosed());
            ps.setString(8, mb.getSummary());
            ps.setString(9, mb.getAssignee());
            ps.setTimestamp(10, mb.getLastChangeTime());
            ps.addBatch();
        }

        System.out.println("executing batch update...");
        ps.executeBatch();
        System.out.println("done...");
    }

    private List<MyBug> searchBugs(Map bugSearch) throws XmlRpcException {
        Map searchResult = (Map) rpcClient.execute("Bug.search", new Object[]{bugSearch});
        Object[] bugs = (Object[]) searchResult.get("bugs");
        List<MyBug> myBugList = new ArrayList();
        MyBug myBug;
        
        for (int i = 0; i < bugs.length; i++) {

            HashMap bug = (HashMap) bugs[i];
            myBug = new MyBug();
            Integer bugId = (Integer) bug.get("id");
            String component = (String) bug.get("component");
            String product = (String) bug.get("product");
            String resolution = (String) bug.get("resolution");
            String status = (String) bug.get("status");
            Long reportedlt = ((java.util.Date) bug.get("creation_time")).getTime();
            Long lastChangeTime = ((java.util.Date) bug.get("last_change_time")).getTime();
            String summary = (String)bug.get("summary");
            String assignee = (String)bug.get("assigned_to");

            myBug.setReported(new Timestamp(reportedlt));
            myBug.setProduct(product);
            myBug.setComponent(component);
            myBug.setResolution(resolution);
            myBug.setStatus(status);
            myBug.setId(bugId);
            myBug.setAssignee(assignee);
            myBug.setSummary(summary);
            myBug.setLastChangeTime(new Timestamp(lastChangeTime));
            myBugList.add(myBug);
        }

        return myBugList;
    }

    public void processHistory(Map bugMap) throws XmlRpcException {
        Map historyResult = (Map) rpcClient.execute("Bug.history", new Object[]{bugMap});
        Object[] bugs = (Object[]) historyResult.get("bugs");
        BugComparator bugComparator = new BugComparator();
        Collections.sort(this.bugList, bugComparator);

        for (int i = 0; i < bugs.length; i++) {
            MyBug myBug = getMyBug(bugs[i]);
            int index = Collections.binarySearch(this.bugList, myBug, bugComparator);
            this.bugList.get(index).setClosed(myBug.getClosed());
        }
    }

    public MyBug getMyBug(Object o) {
//        Map[] history = (HashMap[]) map.get("history
        MyBug myBug = new MyBug();
        HashMap bug = (HashMap) o;
        Integer id = null;
        boolean closed = false;
        String status = null;
        Timestamp timestamp = null;
        //System.out.println(bug.get("id"));
        Object[] history = (Object[]) bug.get("history");
        for (int j = 0; j < history.length; j++) {
            HashMap h = (HashMap) history[j];
            Object[] changes = (Object[]) h.get("changes");

            for (int k = 0; k < changes.length; k++) {
                HashMap c = (HashMap) changes[k];
                //System.out.println(c.get("field_name"));
                if ("bug_status".equalsIgnoreCase(c.get("field_name").toString())) {
                    if ("closed".equalsIgnoreCase(c.get("added").toString())) {
                        //System.out.println(h.get("when"));
                        closed = true;
                        id = (Integer) bug.get("id");
                        status = (String) c.get("added");
                        Long longtime = ((java.util.Date) h.get("when")).getTime();
                        // System.out.println(longtime);
                        timestamp = new Timestamp(longtime);
                        // System.out.println(timestamp);                       
                        //break;
                    } else if ("reopened".equalsIgnoreCase(c.get("added").toString())) {
                        closed = false;
                        id = null;
                        status = null;
                        timestamp = null;
                    }
                }

            }
        }
        if (closed) {
            //to handle for cases where a bug is reopened.
            myBug.setId(id);
            myBug.setStatus(status);
            myBug.setClosed(timestamp);
        }

        return myBug;
    }
}