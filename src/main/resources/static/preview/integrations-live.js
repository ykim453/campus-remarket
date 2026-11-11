let runtimePromise;
let googlePromise;

function runtimeConfig() {
  if (!runtimePromise) {
    runtimePromise = api('integrations/config').catch((error) => {
      runtimePromise = null;
      throw error;
    });
  }
  return runtimePromise;
}

function loadGoogle(key) {
  if (window.google?.maps?.importLibrary) return Promise.resolve();
  if (googlePromise) return googlePromise;
  googlePromise = new Promise((resolve, reject) => {
    window.campusGoogleMapsReady = resolve;
    const script = document.createElement('script');
    script.async = true;
    script.src =
      'https://maps.googleapis.com/maps/api/js?key=' +
      encodeURIComponent(key) +
      '&loading=async&callback=campusGoogleMapsReady&language=ko&region=KR';
    script.onerror = () => {
      googlePromise = null;
      reject(Error('지도를 불러오지 못했습니다.'));
    };
    document.head.append(script);
  });
  return googlePromise;
}

function renderIntegration() {
  $('#section-kicker').textContent = 'CAMPUS TRADE';
  $('#section-title').textContent = '가까운 곳에서 만나요.';
  $('#section-sub').textContent = '거래 장소와 약속한 가격을 확인하세요.';
  const root = $('#results');
  root.className = 'integration-view';
  root.replaceChildren();

  const product = products.find((item) => item.id === tradeProductId) || products[0];
  if (!product) {
    root.append(el('p', 'empty', '상품을 먼저 선택해 주세요.'));
    return;
  }
  tradeProductId = product.id;
  const select = el('select', 'trade-select');
  select.setAttribute('aria-label', '거래할 상품');
  products.forEach((item) =>
    select.append(new Option(item.title + ' · ' + statusText(item), item.id)),
  );
  select.value = product.id;
  select.onchange = () => {
    tradeProductId = Number(select.value);
    history.replaceState(null, '', '#trade/' + tradeProductId);
    renderIntegration();
  };

  const columns = el('div', 'integration-grid');
  const mapPanel = el('article', 'integration-panel');
  const tradePanel = el('article', 'integration-panel');
  columns.append(mapPanel, tradePanel);
  root.append(select, columns);
  renderMeetingMap(mapPanel, product);
  renderTradeSummary(tradePanel, product);
}

function renderTradeSummary(panel, product) {
  const order = product.order;
  panel.append(
    el('h3', '', '거래 약속'),
    el('p', 'trade-product', product.title),
    el('strong', 'trade-amount', formatMoney(order?.agreedPrice ?? displayPrice(product))),
  );
  if (order) panel.append(el('p', 'fine-print', '주문번호 ' + order.orderId));

  if (product.status === 'done') {
    panel.append(el('p', 'locked-price', '✓ 거래 완료'));
    if (order) renderTradeRating(panel, order.orderId);
    return;
  }
  if (!order) {
    if (product.status === 'sale' && product.seller !== actorKey()) {
      panel.append(actionButton('거래 약속 잡기', () => appointmentDialog(product)));
    }
    return;
  }
  if (['PAYMENT_READY', 'WAITING_FOR_DEPOSIT'].includes(order.status)) {
    panel.append(
      el(
        'p',
        'fine-print',
        '관리자 확인 대기 · 이전 버전에서 시작한 거래입니다. 문의할 때 위 주문번호를 알려 주세요.',
      ),
    );
    return;
  }
  if (order.status === 'RESERVED') {
    panel.append(
      actionButton('예약 취소', () => cancelReservation(product), false, 'secondary wide'),
    );
  }
  // 이전 버전에서 이미 승인된 거래도 물품 전달을 마칠 수 있습니다.
  if (order.status === 'RESERVED' || order.status === 'PAID') {
    panel.append(
      actionButton('물건 전달 · 거래 완료', () => completeTrade(product), false, 'secondary wide'),
    );
  }
}

function renderMeetingMap(panel, product) {
  const order = product.order;
  const saved =
    order?.latitude != null
      ? { name: order.meetingPlace, lat: Number(order.latitude), lng: Number(order.longitude) }
      : null;
  let selected = product.customPlace || saved || previewPlaces[product.place || 0];
  const mapNode = el('div', 'campus-map live-map');
  const placeSelect = el('select');
  placeSelect.setAttribute('aria-label', '거래 장소');
  previewPlaces.forEach((place, index) => placeSelect.append(new Option(place.name, index)));
  placeSelect.value = product.place || 0;
  placeSelect.onchange = () => {
    product.place = Number(placeSelect.value);
    product.customPlace = previewPlaces[product.place];
    renderIntegration();
  };
  const chosen = el('p', 'chosen-place');
  const link = el('a', 'map-link', 'Google 지도에서 위치 보기 ↗');
  link.target = '_blank';
  link.rel = 'noopener';
  function showPlace() {
    link.href =
      'https://www.google.com/maps/search/?api=1&query=' +
      encodeURIComponent(selected.lat + ',' + selected.lng);
    chosen.textContent = '📍 ' + selected.name;
  }
  function showEmbeddedMap() {
    const frame = el('iframe');
    frame.title = currentCampus.name + ' 거래 장소 Google 지도';
    frame.loading = 'lazy';
    frame.referrerPolicy = 'no-referrer-when-downgrade';
    frame.src =
      'https://maps.google.com/maps?q=' +
      encodeURIComponent(selected.lat + ',' + selected.lng) +
      '&z=16&output=embed';
    mapNode.replaceChildren(frame);
  }

  const save = actionButton(
    order ? '이 장소로 약속 저장' : '이 장소에서 약속 잡기',
    async () => {
      if (!order) {
        appointmentDialog(product, selected);
        return;
      }
      save.disabled = true;
      const details = {
        meetingPlace: selected.name,
        latitude: selected.lat,
        longitude: selected.lng,
        meetingTime: order.meetingTime,
      };
      const ok = await perform(
        () => api('trades/' + order.orderId + '/meeting', 'PUT', details),
        '거래 장소를 저장했어요.',
        false,
      );
      if (!ok) save.disabled = false;
    },
    order
      ? order.status !== 'RESERVED'
      : product.status !== 'sale' || product.seller === actorKey(),
  );

  panel.append(el('h3', '', '거래 장소 지정'), mapNode, placeSelect, chosen, save, link);
  showPlace();
  showEmbeddedMap();
  runtimeConfig()
    .then(async (config) => {
      if (!config.mapsKey) return;
      await loadGoogle(config.mapsKey);
      const { Map } = await google.maps.importLibrary('maps');
      const { AdvancedMarkerElement } = await google.maps.importLibrary('marker');
      if (!mapNode.isConnected) return;
      mapNode.replaceChildren();
      const map = new Map(mapNode, {
        center: selected,
        zoom: 16,
        mapId: 'DEMO_MAP_ID',
        mapTypeControl: false,
        streetViewControl: false,
      });
      const marker = new AdvancedMarkerElement({ map, position: selected, title: selected.name });
      map.addListener('click', (event) => {
        const lat = event.latLng.lat();
        const lng = event.latLng.lng();
        selected = {
          name: '지도에서 선택한 장소 (' + lat.toFixed(5) + ', ' + lng.toFixed(5) + ')',
          lat,
          lng,
        };
        product.customPlace = selected;
        marker.position = selected;
        showPlace();
      });
    })
    .catch(() => {
      if (mapNode.isConnected) showEmbeddedMap();
    });
}

window.gm_authFailure = () => {
  document.querySelectorAll('.live-map').forEach((node) => {
    node.replaceChildren(
      el('p', 'integration-error', '아래 Google 지도 링크에서 거래 위치를 확인할 수 있어요.'),
    );
  });
};
