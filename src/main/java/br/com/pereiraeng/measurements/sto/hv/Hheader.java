package br.com.pereiraeng.measurements.sto.hv;

import java.io.IOException;
import java.io.RandomAccessFile;

import br.com.pereiraeng.math.timeseries.esp.MedDataType;
import br.com.pereiraeng.physics.Grandeza;

/**
 * Classe do objeto que representa o cabeçalho de uma série de medições
 * horizontais (i.e., medições de diferentes pontos para um mesmo instante)
 * 
 * @author Philipe PEREIRA
 *
 */
public class Hheader extends Header {

	private String[] points;

	public Hheader(String[] points, byte bpm, Grandeza... as) {
		this(points, MedDataType.getDefault(bpm), as);
	}

	public Hheader(String[] points, MedDataType[] dataTypes, Grandeza... as) {
		super(dataTypes, as);
		this.points = points;
	}

	public Hheader(Header h, String[] points) {
		this(points, h.getDataTypes(), h.as);
	}

	public String getPoint(int i) {
		return points[i];
	}

	public int getP() {
		return points.length;
	}

	public String[] getPoints() {
		return points;
	}

	// --------------------------------------------------------

	protected void write(RandomAccessFile raf) throws IOException {
		raf.write(getBpm());

		byte m = getM();
		raf.write(m);
		for (int i = 0; i < m; i++)
			raf.write(getAnaB(i));

		int p = getP();
		raf.writeInt(p);
		for (int i = 0; i < p; i++)
			raf.write((getPoint(i) + "\t").getBytes());
	}

	protected static Hheader read(RandomAccessFile raf) throws IOException {
		byte bpm = (byte) raf.readByte();

		byte m = raf.readByte();
		Grandeza[] anas = new Grandeza[m];
		for (int i = 0; i < m; i++) {
			byte b = raf.readByte();
			if (b > -1)
				anas[i] = Grandeza.values()[b];
		}

		int p = raf.readInt();
		String[] labels = raf.readLine().split("\t");
		if (p != labels.length)
			throw new IllegalArgumentException();

		return new Hheader(labels, MedDataType.getDefault(bpm), anas);
	}
}
