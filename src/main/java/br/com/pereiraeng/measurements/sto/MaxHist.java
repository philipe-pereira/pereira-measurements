package br.com.pereiraeng.measurements.sto;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import br.com.pereiraeng.core.Flow;
import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.io.Comm;
import br.com.pereiraeng.math.timeseries.unit.Med;

public class MaxHist {

	private static Calendar[] offlineLimit;

	public static void get(String folder, Flow<Med> flow, Calendar begin, Calendar end, String... tags) {
		EspMed.get(flow, getFiles(folder, begin, end), begin, end, tags);
	}

	public static void writeFile(String folder, String[] tags, ArrayList<Calendar[]> limits, ArrayList<Med[]> meds) {
		// quebrar registros em meses
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(limits.get(0)[0].getTimeInMillis());
		Calendar end = limits.get(limits.size() - 1)[1];

		Iterator<Calendar[]> it1 = limits.iterator();
		Iterator<Med[]> it2 = meds.iterator();
		Calendar[] p = it1.next();
		Med[] m = it2.next();

		LinkedList<Calendar[]> ls = new LinkedList<>();
		LinkedList<Med[]> ms = new LinkedList<>();

		while (c.before(end)) {
			Calendar[] cs = { (Calendar) p[0].clone(), null };
			c.add(Calendar.MONTH, 1);

			while (p[1].before(c)) {
				ls.add(p);
				ms.add(m);
				cs[1] = (Calendar) p[1].clone();
				if (it1.hasNext()) {
					p = it1.next();
					m = it2.next();
				} else
					break;
			}

			if (!cs[0].before(offlineLimit[0]) && cs[1].before(offlineLimit[1]))
				EspMed.rewriteFile(getFile(folder, cs[0], cs[1]), tags, ls, ms);
			else
				EspMed.writeFile(folder, tags, ls, ms);

			ls.clear();
			ms.clear();
		}
	}

	private static File getFile(String folder, Calendar begin, Calendar end) {
		return new File(String.format("%s/%2$ty%2$tm%2$td%3$ty%3$tm%3$td.ESP", folder, begin, end));
	}

	private static List<File> getFiles(String folder, Calendar begin, Calendar end) {
		List<File> out = new LinkedList<>();

		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(begin.getTimeInMillis());

		Calendar[] range = TimeUtils.getRange(c, Calendar.MONTH);
		while (range[0].before(end)) {
			out.add(getFile(folder, range[0], range[1]));
			c.add(Calendar.MONTH, 1);
			range = TimeUtils.getRange(c, Calendar.MONTH);
		}
		return out;
	}

	// =*=*=*=*=*=*=*=*=*=*=*=*= LIMITES =*=*=*=*=*=*=*=*=*=*=*=*=

	public static void getLimits(int status, String server) {
		if ((status & Comm.FULL_ACCESS) > 0)
			MaxHist.offlineLimit = MaxHist.getPeriod(server);
	}

	public static Calendar[] getLimits() {
		return MaxHist.offlineLimit;
	}

	/**
	 * Função que analisa um diretório com arquivos do MaxHist e informa os
	 * instantes inicial e final do período em que há máximos disponíveis
	 * 
	 * @param folder diretório com os arquivos na forma
	 *               '[yy][mm][dd][yy][mm][dd].ESP'
	 * @return vetor com duas posições, indicando o instante inicial e final
	 */
	public static Calendar[] getPeriod(String folder) {
		String first = "999999", last = "000000";

		Path dir = FileSystems.getDefault().getPath(folder);
		try {
			String glob = "[01][0-9][01][0-9][0123][0-9][01][0-9][01][0-9][0123][0-9].ESP";
			DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob);
			for (Path path : stream) {
				String n = path.getFileName().toString();
				String nf = n.substring(0, 6), nl = n.substring(6, 12);
				if (first.compareTo(nf) > 0)
					first = nf;
				if (last.compareTo(nl) < 0)
					last = nl;
			}
			stream.close();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return null;
		}

		return new Calendar[] {
				new GregorianCalendar(2000 + Integer.parseInt(first.substring(0, 2)),
						Integer.parseInt(first.substring(2, 4)) - 1, Integer.parseInt(first.substring(4, 6))),
				new GregorianCalendar(2000 + Integer.parseInt(last.substring(0, 2)),
						Integer.parseInt(last.substring(2, 4)) - 1, Integer.parseInt(last.substring(4, 6)), 23, 59,
						59) };
	}
}
