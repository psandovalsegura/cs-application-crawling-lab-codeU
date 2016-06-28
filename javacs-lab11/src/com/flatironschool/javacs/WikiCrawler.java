package com.flatironschool.javacs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import redis.clients.jedis.Jedis;


public class WikiCrawler {
	// keeps track of where we started
	private final String source;

	// the index where the results go
	private JedisIndex index;

	// queue of URLs to be indexed
	private Queue<String> queue = new LinkedList<String>();

	// fetcher used to get pages from Wikipedia
	final static WikiFetcher wf = new WikiFetcher();

	/**
	 * Constructor.
	 *
	 * @param source
	 * @param index
	 */
	public WikiCrawler(String source, JedisIndex index) {
		this.source = source;
		this.index = index;
		queue.offer(source);
	}

	/**
	 * Returns the number of URLs in the queue.
	 *
	 * @return
	 */
	public int queueSize() {
		return queue.size();
	}

	/**
	 * Gets a URL from the queue and indexes it.
	 * @param b
	 *
	 * @return Number of pages indexed.
	 * @throws IOException
	 */
	public String crawl(boolean testing) throws IOException {
		//Choose and remove a URL from the queue in FIFO order
		String nextUrl = this.queue.remove();
        if (testing) {
			//Index the page - this process can be more efficient if it also finds internal links
			Elements paragraphs = WikiCrawler.wf.readWikipedia(nextUrl);
			this.index.indexPage(nextUrl, paragraphs);

			//Queue all internal links
			this.queueInternalLinks(paragraphs);

			//Return the url of the page that was indexed
			return nextUrl;
		} else {
			//Check if the URL has already been indexed
			if (this.index.isIndexed(nextUrl)) {
				return null;
			}

			//Read content from the current web
			Elements paragraphs = WikiCrawler.wf.fetchWikipedia(nextUrl);

			//Index the page and queue the links
			this.index.indexPage(nextUrl, paragraphs);
			this.queueInternalLinks(paragraphs);

			return nextUrl;
		}
	}

	/**
	 * Parses paragraphs and adds internal links to the queue. Uses the processTree() helper method
	 *
	 * @param paragraphs
	 */
	// NOTE: absence of access level modifier means package-level
	public void queueInternalLinks(Elements paragraphs) {
		for (Node node: paragraphs) {
			processTree(node);
		}
	}

	/**
	 * Helper method for queueInternalLinks() modified from the TermCounter class
	 *
	 * @param root
	 */
	public void processTree(Node root) {
		// NOTE: we could use select to find the TextNodes, but since
		// we already have a tree iterator, let's use it.
		for (Node node: new WikiNodeIterable(root)) {
			if (node instanceof Element) {
				//Check href attribute
				Element currentElement = (Element) node;
				if (currentElement.tagName().equals("a")) {
					String endpoint = currentElement.attr("href");
					if (verifyWikiEndpoint(endpoint)) {
						this.queue.add("https://en.wikipedia.org" + endpoint);
					}
				}
			}
		}
	}

	/* Helper method determining internal Wikipedia links
	 * @param string endpoint of a links
	 * @return true if page links to a Wikipedia article
	 */
	public boolean verifyWikiEndpoint(String endpoint) {
		if (endpoint.indexOf("/wiki/") == 0) {
			return true;
		} else {
			return false;
		}
	}

	public static void main(String[] args) throws IOException {

		// make a WikiCrawler
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);
		String source = "https://en.wikipedia.org/wiki/Java_(programming_language)";
		WikiCrawler wc = new WikiCrawler(source, index);

		// for testing purposes, load up the queue
		Elements paragraphs = wf.fetchWikipedia(source);
		wc.queueInternalLinks(paragraphs);

		/*
		// loop until we index a new page
		String res;
		do {
			res = wc.crawl(false);

            // REMOVE THIS BREAK STATEMENT WHEN crawl() IS WORKING
            break;
		} while (res == null);

		Map<String, Integer> map = index.getCounts("the");
		for (Entry<String, Integer> entry: map.entrySet()) {
			System.out.println(entry);
		}*/
	}
}
