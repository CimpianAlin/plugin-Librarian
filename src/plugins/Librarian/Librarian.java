package plugins.Librarian;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Vector;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.filter.CommentException;
import freenet.client.filter.FilterCallback;
import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.HTMLEncoder;
import freenet.support.api.HTTPRequest;

public class Librarian implements FredPlugin, FredPluginHTTP, FredPluginThreadless {
	
	private static final String DEFAULT_INDEX_URI = "USK@7H66rhYmxIFgMyw5Dl11JazXGHPhp7dSN7WMa1pbtEo,jQHUQUPTkeRcjmjgrc7t5cDRdDkK3uKkrSzuw5CO9uk,AQACAAE/ENTRY.POINT/11/librarian.idx";
	
	private volatile boolean goon = true;
	Random rnd = new Random();
	PluginRespirator pr;
	private static final String plugName = "Librarian";
	
	public void terminate() {
		goon = false;
	}
	
	public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
		return null;
	}
	
	private void appendDefaultPageStart(StringBuilder out, String stylesheet) {
		out.append("<HTML><HEAD><TITLE>" + plugName + "</TITLE>");
		if(stylesheet != null)
			out.append("<link href=\""+stylesheet+"\" type=\"text/css\" rel=\"stylesheet\" />");
		out.append("</HEAD><BODY>\n");
		out.append("<CENTER><H1>" + plugName + "</H1><BR/><BR/><BR/>\n");
	}

	private void appendDefaultPageEnd(StringBuilder out) {
		out.append("</CENTER></BODY></HTML>");
	}

	private void appendDefaultPostFields(StringBuilder out, String search, String index) {
		search = HTMLEncoder.encode(search);
		index = HTMLEncoder.encode(index);
		out.append("Search for:<br/>");
        out.append("<form method=\"GET\"><input type=\"text\" value=\"").append(search).append("\" name=\"search\" size=80/><br/><br/>");
		out.append("Using the index:<br/>");
        out.append("<input type=text name=\"index\" value=\"").append(index).append("\" size=80/>");
		out.append("<input type=submit value=\"Find!\"/></form>\n");
		// index - key to index
		// search - text to search for
	}
	
	
	private HashMap getFullIndex(String uri) throws Exception {
		HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
		FreenetURI u = new FreenetURI(uri);
		FetchResult res;
		while(true) {
			try {
				res = hlsc.fetch(u);
				break;
			} catch (FetchException e) {
				if(e.newURI != null) {
					u = e.newURI;
					continue;
				} else throw e;
			}
		}
		String index[] = new String(res.asByteArray()).trim().split("\n");
		
		Vector uris = new Vector();
		HashMap keywords = new HashMap();
		
		int i;
		URIWrapper uriw = new URIWrapper();
		for (i = 0 ; i < index.length ; i++) {
			if (index[i].startsWith("!")) {
				/* Start new */
				uriw = new URIWrapper();
				uriw.URI = index[i].substring(1);
				uris.add(uriw);
			} else if (index[i].startsWith("+")) {
				/* Continue old */
				if (uriw.descr==null)
					uriw.descr = index[i].substring(1);
				else
					uriw.descr += '\n' + index[i].substring(1);
			} else
				break;
			
		}
		
		for ( ; i < index.length ; i++) {
			if (!index[i].startsWith("?"))
				break;
			String parts[] = index[i].split(" ");
			Vector keyuris = new Vector();
			//System.err.println(":::" +  + ":::");
			for (int j = 1 ; j < parts.length ; j++) {
				int uriNumber = Integer.parseInt(parts[j]);
				URIWrapper uw = (URIWrapper) uris.get(uriNumber);
				// Yes I know this is O(n), but there shouldn't be that many hits for a single word.
				// FIXME If there are, use a LinkedHashSet (note that this will cost far more memory).
				// Don't use a plain HashSet, because we want the results returned IN ORDER.
				if(!keyuris.contains(uw))
					keyuris.add(uw);
				//System.err.println();
			}
			
			keywords.put(parts[0].substring(1), keyuris);
		}
		
		return keywords;
	}
	
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		StringBuilder out = new StringBuilder();

		//int page = request.getIntParam("page", 1);
		String indexuri = request.getParam("index", DEFAULT_INDEX_URI);
		String search = request.getParam("search");
		String stylesheet = request.getParam("stylesheet", null);
		if(stylesheet != null) {
			FilterCallback cb = pr.makeFilterCallback(request.getPath());
			try {
				stylesheet = cb.processURI(stylesheet, "text/css");
			} catch (CommentException e) {
				return "Invalid stylesheet: "+e.getMessage();
			}
		}
		
		if (search.equals("")) {
			appendDefaultPageStart(out, stylesheet);
			//appendDefaultPostFields(out);
			appendDefaultPostFields(out, search, indexuri);
			appendDefaultPageEnd(out);
			return out.toString();
		}
		
		try {
			/* Cache later! */
			//HighLevelSimpleClient hlsc = pr.getHLSimpleClient();
			//String index = new String(hlsc.fetch(new FreenetURI(indexuri)).asByteArray()).trim();
			/* /Cache!? */
			
			//imglist = imglist.replaceAll("\r","\n");
			
			/*do {
				i = imglist.hashCode();
				imglist = imglist.replaceAll("\n\n", "\n");
			} while (i != imglist.hashCode());*/
			
			//String[] imgarr = imglist.split("\n");
			
			
			HashMap index = getFullIndex(indexuri);
			
			appendDefaultPageStart(out, stylesheet);
			appendDefaultPostFields(out, search, indexuri);

			out.append("<p><span class=\"librarian-searching-for-header\">Searching for: </span><span class=\"librarian-searching-for-target\">").append(HTMLEncoder.encode(search)).append("</span></p>\n");

			//String searchWords[] = search.replaceAll("%20", "+").split("+");
			// Get search result
			String searchWords[] = search.split(" ");
			
			// Return results in order.
			LinkedHashSet hs = new LinkedHashSet();
			synchronized (hs) { // add all for the first word
				Vector keyuris = (Vector)index.get(searchWords[0].toLowerCase().trim());
				if (keyuris != null) {
					Iterator it = keyuris.iterator();
					while (it.hasNext())
						hs.add(it.next());
				}
			}
			synchronized (hs) {
				for (int i = 1 ; i < searchWords.length ; i++) {
					Vector keyuris = (Vector)index.get(searchWords[i].toLowerCase().trim());
					
					Iterator it = hs.iterator();
					while (it.hasNext()) {
						Object o = it.next();
						if (!keyuris.contains(o))
							it.remove();
					}
				}
			}

			// Output results
			int results = 0;
			out.append("<table class=\"librarian-results\"><tr>\n");
			Iterator it = hs.iterator();
			while (it.hasNext()) {
				
				URIWrapper o = (URIWrapper)it.next();
				String showurl = o.URI;
				String description = HTMLEncoder.encode(o.descr);
				description=description.replaceAll("(\n|&lt;(b|B)(r|R)&gt;)", "<br>");
				description=description.replaceAll("  ", "&nbsp; ");
				description=description.replaceAll("&lt;/?[a-zA-Z].*/?&gt;", "");
				showurl = HTMLEncoder.encode(showurl);
				if (showurl.length() > 60)
					showurl = showurl.substring(0,15) + "&hellip;" + 
					    showurl.replaceFirst("[^/]*/", "/");
				String realurl = (o.URI.startsWith("/")?"":"/") + o.URI;
				realurl = HTMLEncoder.encode(realurl);
				out.append("<p>\n<table class=\"librarian-result\" width=\"100%\" border=1><tr><td align=center bgcolor=\"#D0D0D0\" class=\"librarian-result-url\">\n");
				out.append("  <A HREF=\"").append(realurl).append("\" title=\"").append(o.URI).append("\">").append(showurl).append("</A>\n");
				out.append("</td></tr><tr><td align=left class=\"librarian-result-summary\">\n");
				out.append("<tt>").append(description).append("</tt>\n");
				out.append("</td></tr></table>\n");
				results++;
			}
			out.append("</tr><table>\n");
            out.append("<p><span class=\"librarian-summary-found-text\">Found: </span><span class=\"librarian-summary-found-number\">").append(results).append(" results</span></p>\n");
			

			appendDefaultPageEnd(out);
			
			return out.toString();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return e.toString();
		}
	}
	

	public void runPlugin(PluginRespirator pr) {
		this.pr = pr;
		
		//int i = (int)System.currentTimeMillis()%1000;
		//while(goon) {
			/*
			FetchResult fr;
			try {
				fr = pr.getHLSimpleClient().fetch(new FreenetURI("freenet:CHK@j-v1zc0cuN3wlaCpxlKd6vT6c1jAnT9KiscVjfzLu54,q9FIlJSh8M1I1ymRBz~A0fsIcGkvUYZahZb5j7uepLA,AAEA--8"));
				System.err.println("  Got data from key, length = " + fr.size() + " Message: "
						+ new String(fr.asByteArray()).trim());
			} catch (Exception e) {
			}
			*/
		//	try {
		//		Thread.sleep(300000);
		//	} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
		//	}
		//}
	}
	
	private class URIWrapper implements Comparable {
		public String URI;
		public String descr;
		
		public int compareTo(Object o) {
			if (o instanceof URIWrapper)
				return URI.compareTo(((URIWrapper)o).URI);
			return -1;
		}
	}

}
