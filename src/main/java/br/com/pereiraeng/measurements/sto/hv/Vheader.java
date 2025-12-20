package br.com.pereiraeng.measurements.sto.hv;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import br.com.pereiraeng.math.timeseries.esp.MedDataType;
import br.com.pereiraeng.physics.Grandeza;

/**
 * Classe do objeto que representa o cabeçalho de uma série de medições
 * verticais (i.e., medições de diferentes instantes de tempo para um mesmo
 * ponto)
 * 
 * @author Philipe PEREIRA
 *
 */
public class Vheader extends Header {

	private int begin;
	private int end;
	private short step;
	private int n;

	public Vheader(int begin, int end, short step, int n, byte bpm, Grandeza... as) {
		this(begin, end, step, n, MedDataType.getDefault(bpm), as);
	}

	public Vheader(int begin, int end, short step, int n, MedDataType[] dataTypes, Grandeza... as) {
		super(dataTypes, as);
		this.begin = begin;
		this.end = end;
		this.step = step;
		this.n = n;
	}

	public Vheader(Header h, int begin, int end, short step, int n) {
		this(begin, end, step, n, h.getDataTypes(), h.as);
	}

	public int getBegin() {
		return begin;
	}

	public int getEnd() {
		return end;
	}

	public short getStep() {
		return step;
	}

	public int getN() {
		return n;
	}

	protected void write(RandomAccessFile raf) throws IOException {
		byte[] bytes = new byte[4];

		ByteBuffer.wrap(bytes).putInt(getBegin());
		raf.write(bytes);

		ByteBuffer.wrap(bytes).putInt(getEnd());
		raf.write(bytes);

		ByteBuffer.wrap(bytes, 0, 2).putShort(getStep());
		raf.write(bytes, 0, 2);

		ByteBuffer.wrap(bytes).putInt(getN());
		raf.write(bytes);

		raf.write(getBpm());

		byte m = getM();
		raf.write(m);
		for (int i = 0; i < m; i++)
			raf.write(getAnaB(i));
	}

	protected static Vheader read(RandomAccessFile raf) throws IOException {
		byte[] bytes = new byte[4];

		raf.read(bytes);
		int begin = ByteBuffer.wrap(bytes).getInt();

		raf.read(bytes);
		int end = ByteBuffer.wrap(bytes).getInt();

		raf.read(bytes, 0, 2);
		short step = ByteBuffer.wrap(bytes, 0, 2).getShort();

		raf.read(bytes);
		int n = ByteBuffer.wrap(bytes).getInt();

		byte bpm = (byte) raf.readByte();

		byte m = raf.readByte();
		Grandeza[] anas = new Grandeza[m];
		for (int i = 0; i < m; i++) {
			byte b = raf.readByte();
			if (b > -1)
				anas[i] = Grandeza.values()[b];
		}

		return new Vheader(begin, end, step, n, MedDataType.getDefault(bpm), anas);
	}
}
