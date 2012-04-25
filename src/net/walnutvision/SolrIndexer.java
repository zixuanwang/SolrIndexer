package net.walnutvision;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

public class SolrIndexer {

	static class Mapper extends TableMapper<NullWritable, NullWritable> {
		SolrServer mSolrServer = null;

		public void map(ImmutableBytesWritable row, Result values,
				Context context) throws IOException {
			try {
				SolrDocument document = new SolrDocument();
				document.parse(Bytes.toString(values.getRow()));
				SolrInputDocument doc = document.getDocument();
				if (!doc.isEmpty()) {
					mSolrServer.add(doc);
				}
			} catch (SolrServerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void setup(Context context) {
			CategoryConverter.init(CategoryDirectory);
			try {
				mSolrServer = new CommonsHttpSolrServer(SolrServerURL);
			} catch (MalformedURLException e1) {
				e1.printStackTrace();
			}
		}

		@Override
		public void cleanup(Context context) {

		}
	}

	public static void main(String[] args) throws Exception {
		HBaseConfiguration conf = new HBaseConfiguration();
		Job job = new Job(conf, "SolrIndexer");
		job.setJarByClass(SolrIndexer.class);
		job.setMapperClass(Mapper.class);
		job.setNumReduceTasks(0);
		Scan scan = new Scan();
		TableMapReduceUtil.initTableMapperJob("test_item", scan, Mapper.class,
				NullWritable.class, NullWritable.class, job);
		TableMapReduceUtil.initTableReducerJob("test_item", null, job);
		boolean success = job.waitForCompletion(true);
		SolrServer solrServer = new CommonsHttpSolrServer(SolrServerURL);
		solrServer.commit();
		System.exit(success ? 0 : 1);
	}

	public static String SolrServerURL = "http://node1:8080/solr";
	public static String CategoryDirectory = "/export/walnut/workspace/category";
}
