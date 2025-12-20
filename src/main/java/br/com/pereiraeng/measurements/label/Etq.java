package br.com.pereiraeng.measurements.label;
/*
 * Tag.java 
 * Copyright (C) 2020 
 * Cemig Distrição
 */

import java.io.Serializable;

import br.com.pereiraeng.core.Tag;

/**
 * Classe do objeto da <strong>et</strong>i<strong>q</strong>ueta: uma
 * {@link Tag} com uma procedência conhecida
 * 
 * @author Philipe PEREIRA
 * @version July 22, 2020
 *
 */
public class Etq extends Tag implements Serializable {
	private static final long serialVersionUID = 5048899449533003754L;

	/**
	 * id do sistema em que ela é válida
	 */
	protected byte source;

	/**
	 * Construtor de uma etiqueta
	 * 
	 * @param etq        sequência de caracteres que caracterizam de maneira
	 *                   {@link #equals(Object) unívoca} algo
	 * @param externalId id na base de dados externa, -1 se não houver identificação
	 * @param source     id do sistema em que ela é válida
	 */
	public Etq(String etq, int externalId, byte source) {
		super(etq, externalId);
		this.source = source;
	}

	public byte getSource() {
		return source;
	}

	public void setSource(byte source) {
		this.source = source;
	}
}
