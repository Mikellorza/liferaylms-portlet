<%@page import="com.liferay.lms.course.diploma.CourseDiploma"%>
<%@page import="com.liferay.lms.course.diploma.CourseDiplomaRegistry"%>
<%

String pageId ="";
String portletId ="";

CourseDiplomaRegistry cdr=new CourseDiplomaRegistry();
CourseDiploma courseDiploma = cdr.getCourseDiploma();

if(courseDiploma!=null){
	pageId=courseDiploma.getSpecificDiplomaContent();
	portletId=courseDiploma.getPortletId();
}

%>
<liferay-util:include page="<%=pageId %>" portletId="<%=portletId%>" />	
