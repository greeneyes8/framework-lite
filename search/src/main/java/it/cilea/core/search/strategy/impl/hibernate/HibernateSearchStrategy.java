package it.cilea.core.search.strategy.impl.hibernate;

import it.cilea.core.authorization.context.AuthorizationUserHolder;
import it.cilea.core.authorization.model.impl.UserDetail;
import it.cilea.core.displaytag.dto.DisplayTagData;
import it.cilea.core.search.SearchConstant;
import it.cilea.core.search.dao.HibernateStrategyDao;
import it.cilea.core.search.factory.SearchStrategyFactory;
import it.cilea.core.search.model.SearchBuilder;
import it.cilea.core.search.regex.RegexParameterResolver;
import it.cilea.core.search.service.SearchService;
import it.cilea.core.search.strategy.RegexParameterProviderImpl;
import it.cilea.core.search.strategy.SearchStrategy;
import it.cilea.core.search.strategy.SearchStrategyData;
import it.cilea.core.search.util.GsonUtil;
import it.cilea.core.search.util.JaxbUtil;
import it.cilea.core.widget.WidgetConstant;
import it.cilea.core.widget.model.WidgetDictionary;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRichTextString;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class HibernateSearchStrategy implements SearchStrategy {

	@Autowired
	private HibernateStrategyDao hibernateStrategyDao;

	public HibernateSearchStrategy() {
	}

	protected ApplicationContext context;

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

	@Override
	public List<?> getResult(SearchStrategyData d) throws Exception {
		HibernateSearchStrategyData data = (HibernateSearchStrategyData) d;

		List result = new ArrayList();
		if (data.getFilterClause() == null)
			throw new IllegalStateException("The filterClause must not be NULL");
		result = hibernateStrategyDao.getResultList(data);
		return result;
	}

	@Override
	public Long getCount(SearchStrategyData d) throws Exception {
		HibernateSearchStrategyData data = (HibernateSearchStrategyData) d;
		return hibernateStrategyDao.getResultCount(data);
	}

	public void setHibernateStrategyDao(HibernateStrategyDao hibernateStrategyDao) {
		this.hibernateStrategyDao = hibernateStrategyDao;
	}

	public Map getModel(SearchBuilder searchBuilder, HttpServletRequest request, HttpServletResponse response,
			SearchService searchService) throws Exception {
		Map model = new HashMap();
		SearchStrategyData data = SearchStrategyFactory.getStrategyData(searchBuilder, request, searchService);
		model.put("searchBuilderName", searchBuilder.getName());
		if (data.getPost()) {
			if (StringUtils.isBlank(request.getParameter("_mediaType"))
					|| request.getParameter("_mediaType").equals("html")) {

				List<?> resultList;

				resultList = getResult(data);
				Long resultCount = getCount(data);
				String sortField = "";
				if (!ArrayUtils.isEmpty(data.getSortFieldList())) {
					sortField = StringUtils.join(data.getSortFieldList(), ",");
				}
				DisplayTagData displayTagData = new DisplayTagData(resultCount.intValue(), resultList, sortField,
						data.getSortDirection(), data.getPage(), data.getPageSize());
				model.put(SearchConstant.DISPLAY_TAG_DATA, displayTagData);
				List<WidgetDictionary> selectedList = new ArrayList<WidgetDictionary>();

				UserDetail userDetail = AuthorizationUserHolder.getUser();
				if (searchBuilder.getSearchBuilderWidgetLinkSetBySectionMap().get(SearchConstant.SECTION_GROUP)
						.size() != 0) {

					String ids = RegexParameterResolver.getParsedMetaQueryParameterValue(data.getGroupClauseOriginal(),
							request, new RegexParameterProviderImpl());
					ids = StringUtils.remove(ids, "(");
					ids = StringUtils.remove(ids, ")");
					if (StringUtils.isNotBlank(ids))
						for (String id : StringUtils.split(ids, ",")) {
							WidgetDictionary widgetDictionary = WidgetConstant.widgetDictionaryMap.get(Integer
									.valueOf(id));
							if (userDetail == null
									|| widgetDictionary.getAuthorizationResource() == null
									|| userDetail.hasAuthorities(widgetDictionary.getAuthorizationResource()
											.getIdentifier()))
								selectedList.add(widgetDictionary);
						}

				}
				if (searchBuilder.getSearchBuilderWidgetLinkSetBySectionMap().get(SearchConstant.SECTION_SELECT)
						.size() != 0) {

					String ids = RegexParameterResolver.getParsedMetaQueryParameterValue(
							data.getSelectClauseOriginal(), request, new RegexParameterProviderImpl());
					ids = StringUtils.remove(ids, "(");
					ids = StringUtils.remove(ids, ")");
					if (StringUtils.isNotBlank(ids))
						for (String id : StringUtils.split(ids, ",")) {
							WidgetDictionary widgetDictionary = WidgetConstant.widgetDictionaryMap.get(Integer
									.valueOf(id));
							if (userDetail == null
									|| widgetDictionary.getAuthorizationResource() == null
									|| userDetail.hasAuthorities(widgetDictionary.getAuthorizationResource()
											.getIdentifier()))
								selectedList.add(widgetDictionary);
						}
				}
				model.put("selectedList", selectedList);
			} else {
				// export
				handleExport(searchBuilder, request, response, getSql(data), searchService);
			}
		}
		return model;
	}

	@Override
	public Writer getRestMarkup(SearchBuilder searchBuilder, HttpServletRequest request, HttpServletResponse response,
			String outputType, SearchService searchService) throws Exception {

		if (GsonUtil.isGsonEnabled(searchBuilder))
			return GsonUtil.getJson(searchBuilder, this, request, GsonUtil.getDefaultRootModelClass(searchBuilder),
					searchService);
		else
			return JaxbUtil.getRestMarkup(this, searchBuilder, request, response, outputType, searchService);
	}

	public String getSql(SearchStrategyData d) throws Exception {
		HibernateSearchStrategyData data = (HibernateSearchStrategyData) d;
		if (data.getFilterClause() == null)
			throw new IllegalStateException("The filterClause must not be NULL");
		return hibernateStrategyDao.getSqlList(data);
	}

	public Connection getConnection() {
		return hibernateStrategyDao.getConnection();
	}

	private void handleExport(SearchBuilder searchBuilder, HttpServletRequest request, HttpServletResponse response,
			String sqlQuery, SearchService searchService) throws IOException {

		Map<String, Object> model = new HashMap<String, Object>();
		String fileFormat = request.getParameter("_mediaType");
		String type;

		String outputFormat = "";

		if (fileFormat.equals("xls"))
			type = "application/vnd.ms-excel";
		else if (fileFormat.equals("rtf"))
			type = "application/rtf";
		else
			type = "application/" + fileFormat;
		if (fileFormat.equals("csv")) {
			type = "text/csv";
		}
		outputFormat = fileFormat;

		String intestazione = "";
		String contenuto = "";
		Connection conn = null;
		boolean empty = true;
		HSSFWorkbook wb = null;
		try {
			conn = hibernateStrategyDao.getConnection();
			DatabaseMetaData meta = conn.getMetaData();
			ResultSet rs;

			StringBuffer intestazioneBuf = new StringBuffer();
			StringBuffer contenutoBuf = new StringBuffer();

			Statement st = conn.createStatement();
			rs = st.executeQuery(sqlQuery);
			if (fileFormat.equals("xls")) {
				wb = new HSSFWorkbook();
				HSSFSheet sheet = wb.createSheet("-");
				int rowNum = 0;
				int colNum = 0;
				HSSFRow xlsRow = sheet.createRow(rowNum++);
				HSSFCellStyle headerStyle = wb.createCellStyle();
				headerStyle.setFillPattern((short) 2);
				headerStyle.setFillBackgroundColor((short) 54);
				HSSFFont bold = wb.createFont();
				bold.setBoldweight((short) 700);
				bold.setColor((short) 0);
				headerStyle.setFont(bold);
				HSSFCell cell;

				List<WidgetDictionary> selectedList = new ArrayList<WidgetDictionary>();
				UserDetail userDetail = AuthorizationUserHolder.getUser();
				if (searchBuilder.getSearchBuilderWidgetLinkSetBySectionMap().get(SearchConstant.SECTION_GROUP)
						.size() != 0) {
					if (StringUtils.isNotBlank(request.getParameter("gb")))
						for (String id : request.getParameterValues("gb")) {
							WidgetDictionary widgetDictionary = WidgetConstant.widgetDictionaryMap.get(Integer
									.valueOf(id));
							if (userDetail == null
									|| widgetDictionary.getAuthorizationResource() == null
									|| userDetail.hasAuthorities(widgetDictionary.getAuthorizationResource()
											.getIdentifier()))
								selectedList.add(widgetDictionary);
						}

				}
				if (searchBuilder.getSearchBuilderWidgetLinkSetBySectionMap().get(SearchConstant.SECTION_SELECT)
						.size() != 0) {
					if (StringUtils.isNotBlank(request.getParameter("sw")))
						for (String id : request.getParameterValues("sw")) {
							WidgetDictionary widgetDictionary = WidgetConstant.widgetDictionaryMap.get(Integer
									.valueOf(id));
							if (userDetail == null
									|| widgetDictionary.getAuthorizationResource() == null
									|| userDetail.hasAuthorities(widgetDictionary.getAuthorizationResource()
											.getIdentifier()))
								selectedList.add(widgetDictionary);
						}
				}
				for (WidgetDictionary wd : selectedList) {
					cell = xlsRow.createCell((short) colNum++);
					cell.setCellStyle(headerStyle);
					cell.setCellValue(new HSSFRichTextString(wd.getDisplayValue()));
				}

				int conta = 0;
				while (rs.next()) {
					conta++;
					empty = false;
					xlsRow = sheet.createRow(rowNum++);
					colNum = 0;
					for (int i = 1; i < rs.getMetaData().getColumnCount() + 1; i++) {

						if (rs.getMetaData().getColumnType(i) == 2) {
							cell = xlsRow.createCell((short) colNum++, HSSFCell.CELL_TYPE_NUMERIC);
						} else
							cell = xlsRow.createCell((short) colNum++, HSSFCell.CELL_TYPE_STRING);
						Object value = null;
						if (rs.getObject(i) != null)
							value = rs.getObject(i);
						writeCell(value, cell);
					}
				}
			}
			if (fileFormat.equals("csv")) {
				for (int i = 1; i < rs.getMetaData().getColumnCount() + 1; i++)
					intestazioneBuf.append("\"" + StringUtils.replace(rs.getMetaData().getColumnName(i), "\"", "\"\"")
							+ "\";");
				intestazioneBuf.append("\r\n");
				String value = "";
				while (rs.next()) {
					for (int i = 1; i < rs.getMetaData().getColumnCount() + 1; i++) {
						if (rs.getString(i) == null)
							value = "";
						else
							value = rs.getString(i);
						contenutoBuf.append("\"" + StringUtils.replace(value, "\"", "\"\"") + "\";");
					}
					contenutoBuf.append("\r\n");
				}

			}

			contenuto = contenutoBuf.toString();
			intestazione = intestazioneBuf.toString();

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
		if (empty) {
			return;
		} else {
			response.setHeader("Content-Type", type);
			OutputStream output = response.getOutputStream();
			ZipOutputStream out;

			// if (docConfig.isZip()) {
			// out = new ZipOutputStream(output);
			// response.setHeader("Content-Disposition",
			// "Content-Disposition: attachment; filename="
			// + documentBuilder.getMap(mappaIn).get("FILENAME").toString() +
			// "." + outputFormat);
			// out.putNextEntry(new
			// ZipEntry(documentBuilder.getMap(mappaIn).get("FILENAME").toString()
			// + "."
			// + fileFormat));
			//
			// if (fileFormat.equals("xls")) {
			// wb.write(out);
			// } else {
			// out.write(intestazione.getBytes());
			// out.write(contenuto.getBytes());
			// }
			// out.closeEntry();
			// out.close();
			// } else {
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Content-Disposition", "Content-Disposition: attachment; filename=\"list" + "."
					+ outputFormat + "\"");

			if (fileFormat.equals("xls")) {
				wb.write(output);
			} else {
				output.write(intestazione.getBytes());
				output.write(contenuto.getBytes());
			}
			output.flush();
			output.close();
			// }

			return;
		}

	}

	protected void writeCell(Object value, HSSFCell cell) {
		if (value instanceof Number) {
			Number num = (Number) value;
			cell.setCellValue(num.doubleValue());
		} else if (value instanceof Date)
			cell.setCellValue((Date) value);
		else if (value instanceof Calendar)
			cell.setCellValue((Calendar) value);
		else
			cell.setCellValue(new HSSFRichTextString(escapeColumnValue(value)));
	}

	protected String escapeColumnValue(Object rawValue) {
		if (rawValue == null) {
			return null;
		} else {
			String returnString = ObjectUtils.toString(rawValue);
			returnString = StringEscapeUtils.escapeJava(StringUtils.trimToEmpty(returnString));
			returnString = StringUtils.replace(StringUtils.trim(returnString), "\\t", " ");
			returnString = StringUtils.replace(StringUtils.trim(returnString), "\\r", " ");
			returnString = StringEscapeUtils.unescapeJava(returnString);
			return returnString;
		}
	}

}
