/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evalaution of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.pc_sampling.eval.properties;

import org.spldev.evaluation.properties.StringListProperty;

public class GroupingProperty extends StringListProperty {

	public static final String PC_ALL_FM = "pc_all_fm";
	public static final String PC_ALL_FM_FM = "pc_all_fm_fm";
	public static final String PC_FOLDER_FM = "pc_folder_fm";
	public static final String PC_FILE_FM = "pc_file_fm";
	public static final String PC_VARS_FM = "pc_vars_fm";
	public static final String PC_ALL = "pc_all";
	public static final String PC_FOLDER = "pc_folder";
	public static final String PC_FILE = "pc_file";
	public static final String PC_VARS = "pc_vars";
	public static final String FM_ONLY = "fm_only";

	public GroupingProperty() {
		super("grouping");
	}

	public GroupingProperty(String alternateName) {
		super(alternateName);
	}

}
