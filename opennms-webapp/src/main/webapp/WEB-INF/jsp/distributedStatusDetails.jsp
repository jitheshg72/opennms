<%@ page contentType="text/html;charset=UTF-8" language="java"%>
<%@ taglib uri="http://java.sun.com/jstl/core" prefix="c"%>

<jsp:include page="/includes/header.jsp" flush="false">
	<jsp:param name="title" value="Distributed Status Details" />
	<jsp:param name="headTitle" value="Details" />
	<jsp:param name="breadcrumb" value="Distributed Status" />
</jsp:include>

<h3><c:out value="${webTable.title}" /></h3>

<table>

  <tr>
  <c:forEach items="${webTable.columnHeaders}" var="headerCell">
    <th class="<c:out value='${headerCell.styleClass}'/>">
      <c:choose>
        <c:when test="${! empty headerCell.link}">
          <a href="<c:out value='${headerCell.link}'/>"><c:out value="${headerCell.content}"/></a>
        </c:when>
        <c:otherwise>
          <c:out value="${headerCell.content}"/>
        </c:otherwise>
      </c:choose>
    </th>
  </c:forEach>
  </tr>
  
  <c:forEach items="${webTable.rows}" var="row">
    <tr class="<c:out value='${row[0].styleClass}'/>">
      <c:forEach items="${row}" var="cell">
        <td class="<c:out value='${cell.styleClass}'/> divider">
          <c:choose>
            <c:when test="${! empty cell.link}">
	            <a href="<c:out value='${cell.link}'/>"><c:out value="${cell.content}"/></a>
            </c:when>
            <c:otherwise>
 				 <c:out value="${cell.content}"/>
            </c:otherwise>
          </c:choose>
        </td>
      </c:forEach>
    </tr>
  </c:forEach>
</table>

<jsp:include page="/includes/footer.jsp" flush="false"/>
