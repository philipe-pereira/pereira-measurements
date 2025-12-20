package br.com.pereiraeng.measurements.label;
/*
 * MedTag.java 
 * Copyright (C) 2020 
 * Cemig Distrição
 */

import java.io.Serializable;

import br.com.pereiraeng.core.DisplayableFields;
import br.com.pereiraeng.physics.Grandeza;
import br.com.pereiraeng.physics.Propriedade;

/**
 * <p>
 * Classe do objeto da etiqueta de uma medição: uma sequência de caracteres que
 * caracterizam de maneira {@link #equals(Object) unívoca} uma grandeza medida
 * ao longo do tempo.
 * </p>
 * <p>
 * A designação é dada pelo <strong>hospedeiro</strong>: todos os dados aqui não
 * terão sido definidos pelo usuário, mas sim pelo sistema externo que fornece a
 * medição.
 * </p>
 * 
 * @author Philipe Pereira (philipe.mineiro@gmail.com)
 * @version July 08, 2020
 */
public class MedEtq extends Etq implements Serializable, DisplayableFields {
	private static final long serialVersionUID = -2806883017110718802L;

	/**
	 * número de referência da instalação que é a proprietária da label
	 */
	protected int space;

	/**
	 * Grandeza medida pela tag (analógicas ou digitais)
	 */
	protected Propriedade propriedade;

	/**
	 * Construtor de uma etiqueta de medição
	 * 
	 * @param etq         sequência de caracteres que caracterizam de maneira
	 *                    {@link #equals(Object) unívoca} algo
	 * @param externalId  id na base de dados externa, -1 se não houver
	 *                    identificação
	 * @param source      id do sistema em que ela é válida
	 * @param space       número de referência da instalação que é a proprietária da
	 *                    label
	 * @param propriedade propriedade medida pela tag (analógicas ou digitais)
	 */
	public MedEtq(String etq, int externalId, byte source, int space, Propriedade propriedade) {
		super(etq, externalId, source);
		this.space = space;
		this.propriedade = propriedade;
	}

	public MedEtq(int ref, String tag, int space) {
		this(tag, ref, (byte) -1, space, null);
	}

	public MedEtq(String tag) {
		this(-1, tag, -1);
	}

	/**
	 * Função que retorna o número da instalação onde a medição foi feita
	 * 
	 * @return número da instalação
	 */
	public int getSpace() {
		return space;
	}

	/**
	 * Função que estabelece o número da instalação onde a medição foi feita
	 * 
	 * @param space número da instalação
	 */
	public void setSpace(int space) {
		this.space = space;
	}

	public void setPropriedade(Propriedade propriedade) {
		this.propriedade = propriedade;
	}

	/**
	 * Função que indica se a etiqueta representa uma medição análogica (contínua)
	 * ou digital (discreta)
	 * 
	 * @return <code>true</code> para analógica, <code>false</code> para digital
	 */
	public boolean isAnalogic() {
		return propriedade instanceof Grandeza;
	}

	public Grandeza getAnalogic() {
		return (Grandeza) propriedade;
	}

	public Dig getDigital() {
		return (Dig) propriedade;
	}

	// --------------------------- EDITABLE FIELDS ---------------------------

	@Override
	public int getFieldCount() {
		return HEADER.length;
	}

	public static final String[] HEADER = { "Tag", "Fonte", "Grand." };

	@Override
	public String getFieldName(int index) {
		return HEADER[index];
	}

	@Override
	public Object getField(int index) {
		switch (index) {
		case 0:
			return this.etq;
		case 1:
			return this.source;
		case 2:
			return this.propriedade;
		default:
			return null;
		}
	}
}
